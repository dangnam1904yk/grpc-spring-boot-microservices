package com.project.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.project.business.entity.Business;
import com.project.business.repository.BusinessRepository;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.common.security.test.TestJwt;
import com.project.grpc.category.v1.BatchGetCategoriesRequest;
import com.project.grpc.category.v1.BatchGetCategoriesResponse;
import com.project.grpc.category.v1.Category;
import com.project.grpc.category.v1.CategoryServiceGrpc;
import com.project.grpc.category.v1.GetCategoryRequest;
import com.project.grpc.customer.v1.BatchGetCustomersRequest;
import com.project.grpc.customer.v1.BatchGetCustomersResponse;
import com.project.grpc.customer.v1.Customer;
import com.project.grpc.customer.v1.CustomerServiceGrpc;
import com.project.grpc.customer.v1.GetCustomerRequest;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * REST của business-service + gọi gRPC sang category-service và customer-service (giả lập in-process).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.Config.class)
class BusinessApplicationTests {

	/** Customer id N thuộc user "u-N" (xem FakeCustomerService). */
	static final long OWNER_ACTIVE = 1;
	static final long OWNER_LOCKED = 2;
	static final long OWNER_LAZY = 4;
	static final long OWNER_UNKNOWN = 99;

	static final String USER_1 = TestJwt.user("u-1");
	static final String USER_2 = TestJwt.user("u-2");
	static final String USER_4 = TestJwt.user("u-4");
	static final String ADMIN = TestJwt.admin("u-admin");

	@Autowired
	MockMvc mvc;

	@Autowired
	BusinessRepository businessRepository;

	@Autowired
	FakeCategoryService fakeCategory;

	@Autowired
	FakeCustomerService fakeCustomer;

	@BeforeEach
	void reset() {
		businessRepository.deleteAll();
		fakeCategory.batchCalls.set(0);
		fakeCustomer.batchCalls.set(0);
	}

	@Test
	void createValidatesCategoryAndOwnerViaGrpc() throws Exception {
		MvcResult ok = exec(createRequest(USER_1, "B1", "FOOD", OWNER_ACTIVE));
		assertThat(ok.getResponse().getStatus()).isEqualTo(201);
		jsonPath("$.category.name").value("Food").match(ok);
		jsonPath("$.owner.fullName").value("Owner 1").match(ok);

		assertThat(status(createRequest(USER_1, "B2", "UNKNOWN", OWNER_ACTIVE))).isEqualTo(400);
		assertThat(status(createRequest(USER_1, "B3", "OLD", OWNER_ACTIVE))).isEqualTo(400);
		assertThat(status(createRequest(USER_1, "B4", "FOOD", OWNER_UNKNOWN))).isEqualTo(400);
		assertThat(status(createRequest(USER_2, "B5", "FOOD", OWNER_LOCKED))).isEqualTo(400);
		// Trùng code -> chặn trước khi gọi gRPC
		assertThat(status(createRequest(USER_1, "B1", "FOOD", OWNER_ACTIVE))).isEqualTo(409);
	}

	@Test
	void authenticationAndOwnership() throws Exception {
		assertThat(status(createRequest(null, "A1", "FOOD", OWNER_ACTIVE))).isEqualTo(401);
		assertThat(status(get("/api/business"))).isEqualTo(401);
		// u-2 không được tạo business cho customer của u-1
		assertThat(status(createRequest(USER_2, "A2", "FOOD", OWNER_ACTIVE))).isEqualTo(403);
		// ADMIN tạo được cho bất kỳ ai
		assertThat(status(createRequest(ADMIN, "A3", "FOOD", OWNER_ACTIVE))).isEqualTo(201);
	}

	@Test
	void userTokenIsPropagatedToDownstreamServices() throws Exception {
		fakeCustomer.lastCaller.set(null);

		assertThat(status(createRequest(USER_1, "PROP", "FOOD", OWNER_ACTIVE))).isEqualTo(201);

		// customer-service (giả) nhận được đúng user gọi request ban đầu qua gRPC metadata
		assertThat(fakeCustomer.lastCaller.get()).isEqualTo("u-1");
	}

	@Test
	void createCallsCategoryAndCustomerInParallel() throws Exception {
		// LAZY / OWNER_LAZY chỉ trả lời khi CẢ HAI call cùng đang chạy (latch về 0), gọi tuần tự sẽ không đạt
		fakeCategory.parallelProbe.set(new CountDownLatch(2));
		fakeCustomer.parallelProbe.set(fakeCategory.parallelProbe.get());
		fakeCategory.bothInFlight.set(false);

		MvcResult result = exec(createRequest(USER_4, "PAR", "LAZY", OWNER_LAZY));

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		assertThat(fakeCategory.bothInFlight.get()).isTrue();
	}

	@Test
	void slowCategoryService_createFailsFast_readDegrades() throws Exception {
		long start = System.nanoTime();
		MvcResult create = exec(createRequest(USER_1, "SLOW_B", "SLOW", OWNER_ACTIVE));
		assertThat(create.getResponse().getStatus()).isEqualTo(504);
		assertThat(millisSince(start)).isLessThan(1_500);

		Business saved = businessRepository.save(business("SLOW_READ", "SLOW", OWNER_ACTIVE));
		start = System.nanoTime();
		MvcResult read = exec(get("/api/business/{id}", saved.getId()).header("Authorization", TestJwt.bearer(USER_1)));
		assertThat(read.getResponse().getStatus()).isEqualTo(200);
		jsonPath("$.code").value("SLOW_READ").match(read);
		jsonPath("$.category").doesNotExist().match(read);
		// customer-service vẫn trả lời bình thường -> vẫn có owner
		jsonPath("$.owner.id").value(OWNER_ACTIVE).match(read);
		assertThat(millisSince(start)).isLessThan(1_500);
	}

	@Test
	void listUsesOneBatchCallPerService() throws Exception {
		businessRepository.save(business("L1", "FOOD", OWNER_ACTIVE));
		businessRepository.save(business("L2", "FOOD", OWNER_ACTIVE));
		businessRepository.save(business("L3", "OLD", OWNER_LOCKED));

		MvcResult result = exec(get("/api/business").param("size", "10").header("Authorization", TestJwt.bearer(USER_1)));
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		jsonPath("$.totalElements").value(3).match(result);
		jsonPath("$.content[0].category.name").value("Food").match(result);
		jsonPath("$.content[0].owner.fullName").value("Owner 1").match(result);
		jsonPath("$.content[2].category.active").value(false).match(result);
		jsonPath("$.content[2].owner.id").value(OWNER_LOCKED).match(result);
		assertThat(fakeCategory.batchCalls.get()).isEqualTo(1);
		assertThat(fakeCustomer.batchCalls.get()).isEqualTo(1);
	}

	@Test
	void concurrentCreatesWithSameCode_onlyOneWins() throws Exception {
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return status(createRequest(USER_1, "RACE", "FOOD", OWNER_ACTIVE));
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

	/** Thực thi request, tự xử lý trường hợp controller trả CompletableFuture (async). */
	private MvcResult exec(RequestBuilder request) throws Exception {
		MvcResult result = mvc.perform(request).andReturn();
		if (result.getRequest().isAsyncStarted()) {
			result.getAsyncResult(5_000);
			result = mvc.perform(asyncDispatch(result)).andReturn();
		}
		return result;
	}

	private int status(RequestBuilder request) throws Exception {
		return exec(request).getResponse().getStatus();
	}

	/** token = null: gửi không kèm token. */
	private static RequestBuilder createRequest(String token, String code, String categoryCode, long ownerId) {
		MockHttpServletRequestBuilder request = post("/api/business")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"code":"%s","name":"Business %s","categoryCode":"%s","ownerId":%d}"""
						.formatted(code, code, categoryCode, ownerId));
		return token == null ? request : request.header("Authorization", TestJwt.bearer(token));
	}

	private static Business business(String code, String categoryCode, long ownerId) {
		Business b = new Business();
		b.setCode(code);
		b.setName("Business " + code);
		b.setCategoryCode(categoryCode);
		b.setOwnerId(ownerId);
		return b;
	}

	private static long millisSince(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	/** Đợi call còn lại cùng đến (tối đa 400ms, dưới deadline 500ms); ghi nhận nếu cả hai cùng đang chạy. */
	static void awaitPartner(AtomicReference<CountDownLatch> probe, AtomicBoolean bothInFlight) {
		CountDownLatch latch = probe.get();
		latch.countDown();
		try {
			if (latch.await(400, TimeUnit.MILLISECONDS)) {
				bothInFlight.set(true);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@TestConfiguration
	static class FakeServicesConfig {

		@Bean
		@GrpcService
		FakeCategoryService fakeCategoryService() {
			return new FakeCategoryService();
		}

		@Bean
		@GrpcService
		FakeCustomerService fakeCustomerService(FakeCategoryService fakeCategoryService) {
			return new FakeCustomerService(fakeCategoryService.bothInFlight);
		}
	}

	/**
	 * FOOD: active, OLD: inactive, SLOW: phản hồi sau 2s (vượt deadline 500ms),
	 * LAZY: dùng cho test song song, còn lại: NOT_FOUND.
	 */
	static class FakeCategoryService extends CategoryServiceGrpc.CategoryServiceImplBase {

		final AtomicInteger batchCalls = new AtomicInteger();
		final AtomicReference<CountDownLatch> parallelProbe = new AtomicReference<>(new CountDownLatch(0));
		final AtomicBoolean bothInFlight = new AtomicBoolean();

		private final Map<String, Category> categories = Map.of(
				"FOOD", category("FOOD", "Food", true),
				"OLD", category("OLD", "Old", false),
				"SLOW", category("SLOW", "Slow", true),
				"LAZY", category("LAZY", "Lazy", true));

		@Override
		public void getCategory(GetCategoryRequest request, StreamObserver<Category> observer) {
			if ("SLOW".equals(request.getCode())) {
				sleep(2_000);
			}
			if ("LAZY".equals(request.getCode())) {
				awaitPartner(parallelProbe, bothInFlight);
			}
			Category category = categories.get(request.getCode());
			if (category == null) {
				observer.onError(Status.NOT_FOUND.withDescription("not found").asRuntimeException());
				return;
			}
			observer.onNext(category);
			observer.onCompleted();
		}

		@Override
		public void batchGetCategories(BatchGetCategoriesRequest request,
				StreamObserver<BatchGetCategoriesResponse> observer) {
			batchCalls.incrementAndGet();
			BatchGetCategoriesResponse.Builder builder = BatchGetCategoriesResponse.newBuilder();
			request.getCodesList().forEach(code -> {
				if (categories.containsKey(code)) {
					builder.addCategories(categories.get(code));
				} else {
					builder.addMissingCodes(code);
				}
			});
			observer.onNext(builder.build());
			observer.onCompleted();
		}

		private static Category category(String code, String name, boolean active) {
			return Category.newBuilder().setCode(code).setName(name).setActive(active).setVersion(0).build();
		}
	}

	/** 1: active, 2: bị khoá, 4: dùng cho test song song, còn lại: NOT_FOUND. Customer N thuộc user "u-N". */
	static class FakeCustomerService extends CustomerServiceGrpc.CustomerServiceImplBase {

		final AtomicInteger batchCalls = new AtomicInteger();
		final AtomicReference<CountDownLatch> parallelProbe = new AtomicReference<>(new CountDownLatch(0));
		/** User mà server gRPC (giả) xác thực được từ token được chuyển tiếp. */
		final AtomicReference<String> lastCaller = new AtomicReference<>();
		private final AtomicBoolean bothInFlight;

		private final Map<Long, Customer> customers = Map.of(
				OWNER_ACTIVE, customer(OWNER_ACTIVE, true),
				OWNER_LOCKED, customer(OWNER_LOCKED, false),
				OWNER_LAZY, customer(OWNER_LAZY, true));

		FakeCustomerService(AtomicBoolean bothInFlight) {
			this.bothInFlight = bothInFlight;
		}

		@Override
		public void getCustomer(GetCustomerRequest request, StreamObserver<Customer> observer) {
			lastCaller.set(CurrentUser.current().map(AuthUser::id).orElse(null));
			if (request.getId() == OWNER_LAZY) {
				awaitPartner(parallelProbe, bothInFlight);
			}
			Customer customer = customers.get(request.getId());
			if (customer == null) {
				observer.onError(Status.NOT_FOUND.withDescription("not found").asRuntimeException());
				return;
			}
			observer.onNext(customer);
			observer.onCompleted();
		}

		@Override
		public void batchGetCustomers(BatchGetCustomersRequest request,
				StreamObserver<BatchGetCustomersResponse> observer) {
			batchCalls.incrementAndGet();
			BatchGetCustomersResponse.Builder builder = BatchGetCustomersResponse.newBuilder();
			request.getIdsList().forEach(id -> {
				if (customers.containsKey(id)) {
					builder.addCustomers(customers.get(id));
				} else {
					builder.addMissingIds(id);
				}
			});
			observer.onNext(builder.build());
			observer.onCompleted();
		}

		private static Customer customer(long id, boolean active) {
			return Customer.newBuilder().setId(id).setFullName("Owner " + id).setEmail("owner" + id + "@example.com")
					.setUserId("u-" + id).setActive(active).setVersion(0).build();
		}
	}
}
