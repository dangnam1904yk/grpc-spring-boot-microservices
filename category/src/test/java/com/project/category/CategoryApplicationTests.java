package com.project.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.category.repository.CategoryRepository;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditType;
import com.project.common.audit.grpc.AuditMetadata;
import com.project.common.audit.test.CapturingAuditSink;
import com.project.common.security.test.TestJwt;
import com.project.grpc.category.v1.BatchGetCategoriesRequest;
import com.project.grpc.category.v1.BatchGetCategoriesResponse;
import com.project.grpc.category.v1.Category;
import com.project.grpc.category.v1.CategoryServiceGrpc;
import com.project.grpc.category.v1.CreateCategoryRequest;
import com.project.grpc.category.v1.GetCategoryRequest;
import com.project.grpc.category.v1.UpdateCategoryRequest;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Gọi gRPC thật (in-process) + REST vào category-service với DB H2. Token tạo bằng {@link TestJwt}.
 * Audit log được thu lại bằng {@link CapturingAuditSink} thay vì gửi về audit-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestJwt.Config.class, CapturingAuditSink.Config.class })
class CategoryApplicationTests {

	/** Stub không kèm token. */
	@GrpcClient("inProcess")
	CategoryServiceGrpc.CategoryServiceBlockingStub anonymousStub;

	@Autowired
	MockMvc mvc;

	@Autowired
	CapturingAuditSink audit;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ObjectMapper objectMapper;

	/** Stub đăng nhập bằng ADMIN (được thêm/sửa). */
	CategoryServiceGrpc.CategoryServiceBlockingStub stub;
	CategoryServiceGrpc.CategoryServiceBlockingStub userStub;

	@BeforeEach
	void setUpStubs() {
		stub = TestJwt.withToken(anonymousStub, TestJwt.admin("admin-1"));
		userStub = TestJwt.withToken(anonymousStub, TestJwt.user("user-1"));
	}

	@Test
	void createThenGetAndBatchGet() {
		Category created = create("FOOD", "Đồ ăn");

		Category byCode = stub.getCategory(GetCategoryRequest.newBuilder().setCode("FOOD").build());
		assertThat(byCode.getId()).isEqualTo(created.getId());
		assertThat(byCode.getName()).isEqualTo("Đồ ăn");
		assertThat(byCode.hasDescription()).isFalse();

		BatchGetCategoriesResponse batch = stub.batchGetCategories(BatchGetCategoriesRequest.newBuilder()
				.addCodes("FOOD").addCodes("NOPE").build());
		assertThat(batch.getCategoriesList()).extracting(Category::getCode).containsExactly("FOOD");
		assertThat(batch.getMissingCodesList()).containsExactly("NOPE");
	}

	@Test
	void errorsMapToGrpcStatus() {
		create("DUP", "Dup");

		assertStatus(() -> create("DUP", "Dup again"), Status.Code.ALREADY_EXISTS);
		assertStatus(() -> stub.getCategory(GetCategoryRequest.newBuilder().setCode("MISSING").build()),
				Status.Code.NOT_FOUND);
		assertStatus(() -> stub.getCategory(GetCategoryRequest.getDefaultInstance()), Status.Code.INVALID_ARGUMENT);
		assertStatus(() -> stub.createCategory(CreateCategoryRequest.newBuilder()
				.setCode("CHILD").setName("Child").setParentCode("NO_PARENT").build()), Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void grpcRequiresTokenAndOnlyAdminCanWrite() {
		create("SEC", "Sec");
		GetCategoryRequest get = GetCategoryRequest.newBuilder().setCode("SEC").build();

		assertStatus(() -> anonymousStub.getCategory(get), Status.Code.UNAUTHENTICATED);
		assertStatus(() -> TestJwt.withToken(anonymousStub, "invalid.jwt.token").getCategory(get),
				Status.Code.UNAUTHENTICATED);
		assertThat(userStub.getCategory(get).getCode()).isEqualTo("SEC");
		assertStatus(() -> userStub.createCategory(CreateCategoryRequest.newBuilder()
				.setCode("USER_MADE").setName("x").build()), Status.Code.PERMISSION_DENIED);
	}

	@Test
	void restReadIsPublicButWriteNeedsAdmin() throws Exception {
		create("PUB", "Public");
		String body = "{\"code\":\"REST1\",\"name\":\"Rest\"}";

		assertThat(status(get("/api/category/PUB"))).isEqualTo(200);
		assertThat(status(post("/api/category").contentType(MediaType.APPLICATION_JSON).content(body)))
				.isEqualTo(401);
		assertThat(status(post("/api/category").header("Authorization", TestJwt.bearer(TestJwt.user("u-2")))
				.contentType(MediaType.APPLICATION_JSON).content(body))).isEqualTo(403);
		assertThat(status(post("/api/category").header("Authorization", TestJwt.bearer(TestJwt.admin("a-2")))
				.contentType(MediaType.APPLICATION_JSON).content(body))).isEqualTo(201);
	}

	@Test
	void staleVersionIsRejected() {
		Category created = create("VER", "v0");
		stub.updateCategory(update(created, "v1"));

		// Vẫn gửi version cũ -> bị từ chối, không ghi đè
		assertStatus(() -> stub.updateCategory(update(created, "v2")), Status.Code.ABORTED);
		assertThat(stub.getCategory(GetCategoryRequest.newBuilder().setCode("VER").build()).getName())
				.isEqualTo("v1");
	}

	@Test
	void concurrentCreatesWithSameCode_onlyOneWins() throws Exception {
		List<Status.Code> results = runConcurrently(10, () -> create("RACE", "Race"));

		assertThat(results).filteredOn(c -> c == Status.Code.OK).hasSize(1);
		assertThat(results).filteredOn(c -> c != Status.Code.OK).containsOnly(Status.Code.ALREADY_EXISTS);
	}

	@Test
	void concurrentUpdatesWithSameVersion_onlyOneWins() throws Exception {
		Category created = create("RACE_UPD", "v0");

		List<Status.Code> results = runConcurrently(10, () -> stub.updateCategory(update(created, "updated")));

		assertThat(results).filteredOn(c -> c == Status.Code.OK).hasSize(1);
		assertThat(results).filteredOn(c -> c != Status.Code.OK).containsOnly(Status.Code.ABORTED);
	}

	@Test
	void restWriteIsAuditedWithIpCurlBodiesAndFieldChanges() throws Exception {
		String token = TestJwt.admin("auditor");
		MvcResult created = mvc.perform(post("/api/category")
				.header("Authorization", TestJwt.bearer(token))
				.header("X-Request-Id", "req-rest-create")
				.header("X-Forwarded-For", "1.1.1.1, 203.0.113.7")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"AUD\",\"name\":\"Audit\"}"))
				.andReturn();
		assertThat(created.getResponse().getStatus()).isEqualTo(201);
		assertThat(created.getResponse().getHeader("X-Request-Id")).isEqualTo("req-rest-create");

		AuditEvent http = single("req-rest-create", AuditType.HTTP);
		assertThat(http.clientIp()).isEqualTo("203.0.113.7");
		assertThat(http.userId()).isEqualTo("auditor");
		assertThat(http.status()).isEqualTo("201");
		assertThat(http.requestBody()).contains("\"code\":\"AUD\"");
		assertThat(http.responseBody()).contains("\"name\":\"Audit\"");
		assertThat(http.curl()).startsWith("curl -X POST 'http://localhost/api/category'")
				.contains("Authorization: Bearer ***")
				.doesNotContain(token);

		AuditEvent insert = single("req-rest-create", AuditType.ENTITY_CHANGE);
		assertThat(insert.action()).isEqualTo("CREATE");
		assertThat(insert.entityName()).isEqualTo("Category");
		assertThat(insert.userId()).isEqualTo("auditor");
		assertThat(changes(insert)).anySatisfy(c -> {
			assertThat(c.get("field").asText()).isEqualTo("code");
			assertThat(c.get("new").asText()).isEqualTo("AUD");
		});

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
		UUID id = UUID.fromString(body.get("id").asText());
		assertThat(id.version()).isEqualTo(7);
		// Sửa bằng ADMIN khác để kiểm tra BaseEntity ghi đúng người tạo / người sửa
		mvc.perform(put("/api/category/" + id)
				.header("Authorization", TestJwt.bearer(TestJwt.admin("editor")))
				.header("X-Request-Id", "req-rest-update")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Audit 2\",\"active\":true,\"version\":" + body.get("version") + "}"));

		AuditEvent update = single("req-rest-update", AuditType.ENTITY_CHANGE);
		assertThat(update.action()).isEqualTo("UPDATE");
		assertThat(update.userId()).isEqualTo("editor");
		assertThat(update.entityId()).isEqualTo(body.get("id").asText());
		// Chỉ ghi field thực sự đổi, kèm giá trị cũ -> mới
		assertThat(changes(update)).extracting(c -> c.get("field").asText())
				.contains("name", "version", "lastModify", "lastModifyDate")
				.doesNotContain("code", "createBy", "createDate");
		assertThat(changes(update)).anySatisfy(c -> {
			assertThat(c.get("field").asText()).isEqualTo("name");
			assertThat(c.get("old").asText()).isEqualTo("Audit");
			assertThat(c.get("new").asText()).isEqualTo("Audit 2");
		});
	}

	@Test
	void baseEntityRecordsWhoAndWhen() throws Exception {
		Instant before = Instant.now();
		Category created = TestJwt.withToken(anonymousStub, TestJwt.admin("creator"))
				.createCategory(CreateCategoryRequest.newBuilder().setCode("BASE").setName("Base").build());
		UUID id = UUID.fromString(created.getId());

		com.project.category.entity.Category saved = categoryRepository.findById(id).orElseThrow();
		assertThat(saved.getCreateBy()).isEqualTo("creator");
		assertThat(saved.getLastModify()).isEqualTo("creator");
		assertThat(saved.getCreateDate()).isBetween(before, Instant.now());
		assertThat(saved.getLastModifyDate()).isEqualTo(saved.getCreateDate());

		TestJwt.withToken(anonymousStub, TestJwt.admin("editor")).updateCategory(update(created, "Base 2"));

		com.project.category.entity.Category updated = categoryRepository.findById(id).orElseThrow();
		assertThat(updated.getCreateBy()).isEqualTo("creator");
		assertThat(updated.getCreateDate()).isEqualTo(saved.getCreateDate());
		assertThat(updated.getLastModify()).isEqualTo("editor");
		assertThat(updated.getLastModifyDate()).isAfter(saved.getLastModifyDate());
	}

	@Test
	void uuidV7IdsAreTimeOrdered() {
		UUID first = UUID.fromString(create("ORD1", "Order 1").getId());
		UUID second = UUID.fromString(create("ORD2", "Order 2").getId());

		assertThat(first.version()).isEqualTo(7);
		assertThat(second).isGreaterThan(first);
		assertStatus(() -> stub.getCategory(GetCategoryRequest.newBuilder().setId("not-a-uuid").build()),
				Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void rejectedRestRequestIsAuditedWithoutChanges() throws Exception {
		mvc.perform(post("/api/category").header("X-Request-Id", "req-rest-401")
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NOPE401\",\"name\":\"x\"}"));

		AuditEvent http = single("req-rest-401", AuditType.HTTP);
		assertThat(http.status()).isEqualTo("401");
		assertThat(http.userId()).isNull();
		assertThat(audit.find("req-rest-401", AuditType.ENTITY_CHANGE)).isEmpty();
	}

	@Test
	void grpcCallIsAuditedAndChangesLinkToCallerRequestId() {
		Metadata headers = new Metadata();
		headers.put(AuditMetadata.REQUEST_ID, "req-grpc-create");
		headers.put(AuditMetadata.CLIENT_IP, "198.51.100.9");
		stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
				.createCategory(CreateCategoryRequest.newBuilder().setCode("AUD_GRPC").setName("Grpc").build());

		AuditEvent grpc = single("req-grpc-create", AuditType.GRPC);
		assertThat(grpc.method()).isEqualTo("category.v1.CategoryService/CreateCategory");
		assertThat(grpc.status()).isEqualTo("OK");
		assertThat(grpc.clientIp()).isEqualTo("198.51.100.9");
		assertThat(grpc.userId()).isEqualTo("admin-1");
		assertThat(grpc.requestBody()).contains("AUD_GRPC");
		assertThat(grpc.responseBody()).contains("AUD_GRPC");

		AuditEvent insert = single("req-grpc-create", AuditType.ENTITY_CHANGE);
		assertThat(insert.action()).isEqualTo("CREATE");
		assertThat(insert.userId()).isEqualTo("admin-1");
		assertThat(insert.clientIp()).isEqualTo("198.51.100.9");
	}

	@Test
	void failedGrpcWriteIsAuditedWithoutEntityChange() {
		Metadata headers = new Metadata();
		headers.put(AuditMetadata.REQUEST_ID, "req-grpc-fail");
		assertStatus(() -> stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
				.createCategory(CreateCategoryRequest.newBuilder().setCode("AUD_FAIL").setName("x")
						.setParentCode("NO_SUCH_PARENT").build()),
				Status.Code.INVALID_ARGUMENT);

		assertThat(single("req-grpc-fail", AuditType.GRPC).status()).isEqualTo("INVALID_ARGUMENT");
		assertThat(audit.find("req-grpc-fail", AuditType.ENTITY_CHANGE)).isEmpty();
	}

	@Test
	void grpcCallWithoutTokenIsAudited() {
		Metadata headers = new Metadata();
		headers.put(AuditMetadata.REQUEST_ID, "req-grpc-401");
		assertStatus(() -> anonymousStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
				.getCategory(GetCategoryRequest.newBuilder().setCode("ANY").build()), Status.Code.UNAUTHENTICATED);

		AuditEvent grpc = single("req-grpc-401", AuditType.GRPC);
		assertThat(grpc.status()).isEqualTo("UNAUTHENTICATED");
		assertThat(grpc.userId()).isNull();
	}

	private AuditEvent single(String requestId, AuditType type) {
		List<AuditEvent> events = audit.find(requestId, type);
		assertThat(events).as("%s log của %s", type, requestId).hasSize(1);
		return events.get(0);
	}

	private List<JsonNode> changes(AuditEvent event) throws Exception {
		List<JsonNode> result = new ArrayList<>();
		objectMapper.readTree(event.changes()).forEach(result::add);
		return result;
	}

	private Category create(String code, String name) {
		return stub.createCategory(CreateCategoryRequest.newBuilder().setCode(code).setName(name).build());
	}

	private int status(RequestBuilder request) throws Exception {
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}

	private static UpdateCategoryRequest update(Category current, String newName) {
		return UpdateCategoryRequest.newBuilder()
				.setId(current.getId())
				.setName(newName)
				.setActive(true)
				.setVersion(current.getVersion())
				.build();
	}

	private static void assertStatus(Runnable call, Status.Code expected) {
		assertThatThrownBy(call::run)
				.isInstanceOf(StatusRuntimeException.class)
				.extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
				.isEqualTo(expected);
	}

	/** Chạy cùng một call trên nhiều thread, xuất phát cùng lúc. */
	private static List<Status.Code> runConcurrently(int threads, Runnable call) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Status.Code>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				Callable<Status.Code> task = () -> {
					start.await();
					try {
						call.run();
						return Status.Code.OK;
					} catch (StatusRuntimeException e) {
						return e.getStatus().getCode();
					}
				};
				futures.add(pool.submit(task));
			}
			start.countDown();
			List<Status.Code> results = new ArrayList<>();
			for (Future<Status.Code> f : futures) {
				results.add(f.get());
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}
}
