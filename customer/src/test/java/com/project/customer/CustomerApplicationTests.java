package com.project.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.security.test.TestJwt;
import com.project.grpc.customer.v1.BatchGetCustomersRequest;
import com.project.grpc.customer.v1.BatchGetCustomersResponse;
import com.project.grpc.customer.v1.Customer;
import com.project.grpc.customer.v1.CustomerServiceGrpc;
import com.project.grpc.customer.v1.GetCustomerRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * REST (người dùng: tạo/sửa hồ sơ của mình) + gRPC (service nội bộ đọc) của customer-service với DB H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.Config.class)
class CustomerApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	private static final String MISSING_ID = "00000000-0000-7000-8000-000000000000";

	@GrpcClient("inProcess")
	CustomerServiceGrpc.CustomerServiceBlockingStub anonymousStub;

	@Test
	void createOwnProfileViaRestThenReadViaGrpc() throws Exception {
		String token = TestJwt.token("u-an", "An@Example.com", "USER");
		JsonNode created = createCustomer(token, "An Nguyen");
		String id = created.get("id").asText();
		// email + userId lấy từ token, không phải từ body
		assertThat(created.get("email").asText()).isEqualTo("an@example.com");
		assertThat(created.get("userId").asText()).isEqualTo("u-an");

		MvcResult me = mvc.perform(withToken(get("/api/customer/me"), token)).andReturn();
		assertThat(json(me).get("id").asText()).isEqualTo(id);

		CustomerServiceGrpc.CustomerServiceBlockingStub stub = TestJwt.withToken(anonymousStub, token);
		Customer byId = stub.getCustomer(GetCustomerRequest.newBuilder().setId(id).build());
		assertThat(byId.getFullName()).isEqualTo("An Nguyen");
		assertThat(byId.getUserId()).isEqualTo("u-an");
		assertThat(stub.getCustomer(GetCustomerRequest.newBuilder().setEmail("AN@example.com").build()).getId())
				.isEqualTo(id);

		BatchGetCustomersResponse batch = stub.batchGetCustomers(BatchGetCustomersRequest.newBuilder()
				.addIds(id).addIds(MISSING_ID).build());
		assertThat(batch.getCustomersList()).extracting(Customer::getId).containsExactly(id);
		assertThat(batch.getMissingIdsList()).containsExactly(MISSING_ID);
		// id không phải UUID -> INVALID_ARGUMENT, không phải lỗi server
		assertThatThrownBy(() -> stub.getCustomer(GetCustomerRequest.newBuilder().setId("123").build()))
				.isInstanceOf(StatusRuntimeException.class)
				.extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
				.isEqualTo(Status.Code.INVALID_ARGUMENT);

		assertThatThrownBy(() -> anonymousStub.getCustomer(GetCustomerRequest.newBuilder().setId(id).build()))
				.isInstanceOf(StatusRuntimeException.class)
				.extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
				.isEqualTo(Status.Code.UNAUTHENTICATED);
	}

	@Test
	void restValidationAndErrors() throws Exception {
		String token = TestJwt.user("u-binh");
		createCustomer(token, "Binh");

		assertThat(postCustomer(token, "Binh 2").getResponse().getStatus()).isEqualTo(409);
		MvcResult invalid = mvc.perform(withToken(post("/api/customer"), TestJwt.user("u-other"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\",\"phone\":\"abc\"}")).andReturn();
		assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
		jsonPath("$.errors.fullName").exists().match(invalid);
		jsonPath("$.errors.phone").exists().match(invalid);

		assertThat(mvc.perform(post("/api/customer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"No token\"}")).andReturn().getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void onlyOwnerOrAdminCanReadAndUpdate() throws Exception {
		String owner = TestJwt.user("u-chi");
		String stranger = TestJwt.user("u-stranger");
		String admin = TestJwt.admin("u-admin");
		JsonNode created = createCustomer(owner, "Chi");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		assertThat(status(withToken(get("/api/customer/{id}", id), owner))).isEqualTo(200);
		assertThat(status(withToken(get("/api/customer/{id}", id), stranger))).isEqualTo(403);
		assertThat(status(withToken(get("/api/customer/{id}", id), admin))).isEqualTo(200);
		assertThat(status(withToken(get("/api/customer"), owner))).isEqualTo(403);
		assertThat(status(withToken(get("/api/customer"), admin))).isEqualTo(200);

		assertThat(update(stranger, id, "Hacked", true, version).getResponse().getStatus()).isEqualTo(403);
		// Chủ hồ sơ không được tự khoá / mở khoá
		assertThat(update(owner, id, "Chi", false, version).getResponse().getStatus()).isEqualTo(403);
		assertThat(update(admin, id, "Chi", false, version).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void staleVersionUpdateIsRejected() throws Exception {
		String token = TestJwt.user("u-ver");
		JsonNode created = createCustomer(token, "Ver");
		String id = created.get("id").asText();
		long version = created.get("version").asLong();

		assertThat(update(token, id, "Ver v1", true, version).getResponse().getStatus()).isEqualTo(200);
		assertThat(update(token, id, "Ver v2", true, version).getResponse().getStatus()).isEqualTo(409);
	}

	@Test
	void concurrentProfileCreationForSameUser_onlyOneWins() throws Exception {
		String token = TestJwt.user("u-race");
		int threads = 10;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return postCustomer(token, "Race").getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> f : futures) {
				statuses.add(f.get());
			}
			assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
			assertThat(statuses).filteredOn(s -> s != 201).containsOnly(409);
		} finally {
			pool.shutdownNow();
		}
	}

	private JsonNode createCustomer(String token, String fullName) throws Exception {
		MvcResult result = postCustomer(token, fullName);
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return json(result);
	}

	private MvcResult postCustomer(String token, String fullName) throws Exception {
		return mvc.perform(withToken(post("/api/customer"), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"%s\",\"phone\":\"0912345678\"}".formatted(fullName)))
				.andReturn();
	}

	private MvcResult update(String token, String id, String fullName, boolean active, long version)
			throws Exception {
		return mvc.perform(withToken(put("/api/customer/{id}", id), token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"%s\",\"active\":%s,\"version\":%d}".formatted(fullName, active, version)))
				.andReturn();
	}

	private int status(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}

	private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
		return request.header("Authorization", TestJwt.bearer(token));
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
