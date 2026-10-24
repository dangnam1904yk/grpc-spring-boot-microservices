package com.project.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.RequestBuilder;

import com.project.common.security.test.TestJwt;
import com.project.grpc.category.v1.BatchGetCategoriesRequest;
import com.project.grpc.category.v1.BatchGetCategoriesResponse;
import com.project.grpc.category.v1.Category;
import com.project.grpc.category.v1.CategoryServiceGrpc;
import com.project.grpc.category.v1.CreateCategoryRequest;
import com.project.grpc.category.v1.GetCategoryRequest;
import com.project.grpc.category.v1.UpdateCategoryRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Gọi gRPC thật (in-process) + REST vào category-service với DB H2. Token tạo bằng {@link TestJwt}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.Config.class)
class CategoryApplicationTests {

	/** Stub không kèm token. */
	@GrpcClient("inProcess")
	CategoryServiceGrpc.CategoryServiceBlockingStub anonymousStub;

	@Autowired
	MockMvc mvc;

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
