package com.project.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.auth.entity.UserAccount;
import com.project.auth.repository.UserAccountRepository;
import com.project.auth.repository.UserRoleRepository;
import com.project.common.security.test.TestJwt;
import com.project.grpc.auth.v1.AuthorizationServiceGrpc;
import com.project.grpc.auth.v1.GetAuthoritiesRequest;
import com.project.grpc.auth.v1.GetAuthoritiesResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Tài khoản do Keycloak giữ (FakeKeycloakAdmin); role của user nằm ở DB auth-service, trả cho service khác qua
 * gRPC GetAuthorities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(KeycloakTestConfig.class)
class AuthApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	FakeKeycloakAdmin keycloak;

	@Autowired
	TestUsers users;

	@Autowired
	UserRoleRepository userRoleRepository;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	RecordingAuthorityCache cache;

	@GrpcClient("inProcess")
	AuthorizationServiceGrpc.AuthorizationServiceBlockingStub anonymousStub;

	String admin;

	@BeforeEach
	void setUpAdmin() {
		admin = users.admin();
	}

	@Test
	void meReadsProfileFromKeycloakAndRolesFromDb() throws Exception {
		String id = users.register("an@example.com");
		JsonNode me = ok(get("/api/auth/me"), users.token(id), null);
		assertThat(me.get("id").asText()).isEqualTo(id);
		assertThat(me.get("email").asText()).isEqualTo("an@example.com");
		assertThat(me.get("roles").toString()).isEqualTo("[\"USER\"]");

		assertThat(status(get("/api/auth/me"), null, null)).isEqualTo(401);
		assertThat(status(get("/api/auth/me"), "not-a-jwt", null)).isEqualTo(401);
	}

	@Test
	void rolesInTokenAreIgnoredAndOnlyAdminGrantsAdmin() throws Exception {
		String userId = users.register("promote@example.com");
		// Token tự nhận là ADMIN / có user:EDIT: auth-service không tin claim, chỉ tin DB
		String forged = TestJwt.token(userId, "promote@example.com", List.of("USER", "ADMIN"), List.of("user:EDIT"));
		String body = "{\"roles\":[\"ADMIN\"]}";
		assertThat(status(put("/api/auth/users/{id}/roles", userId), forged, body)).isEqualTo(403);

		JsonNode promoted = ok(put("/api/auth/users/{id}/roles", userId), admin, body);
		assertThat(promoted.get("roles").toString()).isEqualTo("[\"ADMIN\",\"USER\"]");
		assertThat(ok(get("/api/auth/me"), users.token(userId), null).get("roles").toString())
				.isEqualTo("[\"ADMIN\",\"USER\"]");
		// Role không nằm trong token nên không phải đăng xuất user
		assertThat(keycloak.logoutCount(userId)).isZero();
	}

	@Test
	void adminCreatesUserAndDuplicateEmailIsRejected() throws Exception {
		String body = """
				{"email":"Clerk@Example.com","password":"secret123","fullName":" Trần Thị Clerk ",
				 "phoneNumber":"0912345678","status":"INACTIVE"}""";
		JsonNode created = ok(post("/api/auth/users"), admin, body);
		assertThat(created.get("email").asText()).isEqualTo("clerk@example.com");
		assertThat(created.get("fullName").asText()).isEqualTo("Trần Thị Clerk");
		assertThat(created.get("status").asText()).isEqualTo("INACTIVE");
		assertThat(created.get("roles").toString()).isEqualTo("[\"USER\"]");

		assertThat(status(post("/api/auth/users"), admin, body)).isEqualTo(409);
		assertThat(status(post("/api/auth/users"), users.token(users.register("plain@example.com")), body))
				.isEqualTo(403);
	}

	@Test
	void grpcReturnsAuthoritiesOfCaller() throws Exception {
		ok(post("/api/auth/roles"), admin,
				"{\"code\":\"GRPC_EDITOR\",\"name\":\"Biên tập\",\"permissions\":[\"category:ADD\",\"category:EDIT\"]}");
		String userId = users.register("grpc-editor@example.com", "GRPC_EDITOR");

		GetAuthoritiesResponse mine = TestJwt.withToken(anonymousStub, users.token(userId))
				.getAuthorities(GetAuthoritiesRequest.getDefaultInstance());
		assertThat(mine.getRolesList()).containsExactly("GRPC_EDITOR", "USER");
		assertThat(mine.getPermissionsList()).containsExactly("category:ADD", "category:EDIT");

		assertThat(TestJwt.withToken(anonymousStub, admin).getAuthorities(GetAuthoritiesRequest.getDefaultInstance())
				.getPermissionsList()).contains("category:ADD", "role:DELETE", "audit:VIEW");

		assertThatThrownBy(() -> anonymousStub.getAuthorities(GetAuthoritiesRequest.getDefaultInstance()))
				.isInstanceOfSatisfying(StatusRuntimeException.class,
						e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
	}

	@Test
	void authorityChangesEvictSharedCache() throws Exception {
		JsonNode role = ok(post("/api/auth/roles"), admin, "{\"code\":\"CACHE_TEST\",\"name\":\"Cache\"}");
		String userId = users.register("cache@example.com");

		ok(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"CACHE_TEST\"]}");
		assertThat(cache.evictedUsers).contains(userId);

		int before = cache.evictAllCount.get();
		ok(put("/api/auth/roles/{id}/permissions", role.get("id").asText()), admin,
				"{\"permissions\":[\"category:ADD\"],\"version\":%d}".formatted(role.get("version").asLong()));
		assertThat(cache.evictAllCount.get()).isEqualTo(before + 1);

		// Request lỗi (rollback): không xoá cache
		int afterUpdate = cache.evictAllCount.get();
		assertThat(status(put("/api/auth/roles/{id}/permissions", role.get("id").asText()), admin,
				"{\"permissions\":[\"category:FLY\"],\"version\":99}")).isGreaterThanOrEqualTo(400);
		assertThat(cache.evictAllCount.get()).isEqualTo(afterUpdate);
	}

	@Test
	void firstAdminIsGrantedToConfiguredVerifiedEmailOnlyWhileNoAdminExists() throws Exception {
		String id = users.register("admin@example.com");
		// Đã có ADMIN (setUpAdmin): không tự gán
		assertThat(roles(users.verifiedToken(id))).isEqualTo("[\"USER\"]");

		userRoleRepository.deleteAll(userRoleRepository.findByRole("ADMIN"));
		// Email chưa xác minh: không tự gán
		assertThat(roles(users.token(id))).isEqualTo("[\"USER\"]");
		assertThat(roles(users.verifiedToken(id))).isEqualTo("[\"ADMIN\",\"USER\"]");
		assertThat(userRoleRepository.findByRole("ADMIN")).singleElement()
				.satisfies(r -> assertThat(r.getUserId()).isEqualTo(id));
	}

	@Test
	void registerCreatesKeycloakUserAndLocalRowThenLogsIn() throws Exception {
		String body = """
				{"email":" Reg@Example.com ","password":"secret123","fullName":" Nguyễn Văn Reg ","phoneNumber":"0912345678"}""";
		JsonNode tokens = ok(post("/api/auth/register"), null, body);
		assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
		assertThat(tokens.get("refreshToken").asText()).isNotBlank();

		JsonNode me = ok(get("/api/auth/me"), tokens.get("accessToken").asText(), null);
		assertThat(me.get("email").asText()).isEqualTo("reg@example.com");
		assertThat(me.get("roles").toString()).isEqualTo("[\"USER\"]");
		UserAccount account = userAccountRepository.findByUserId(me.get("id").asText()).orElseThrow();
		assertThat(account.getEmail()).isEqualTo("reg@example.com");
		assertThat(account.getFullName()).isEqualTo("Nguyễn Văn Reg");
		assertThat(account.getPhoneNumber()).isEqualTo("0912345678");
		assertThat(account.isEnabled()).isTrue();

		assertThat(status(post("/api/auth/register"), null, body)).isEqualTo(409);
	}

	@Test
	void registerValidatesInputWithTranslatedFieldNames() throws Exception {
		MvcResult result = exec(post("/api/auth/register").header("Accept-Language", "vi"), null,
				"{\"email\":\"not-an-email\",\"password\":\"short\"}");
		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		JsonNode errors = objectMapper.readTree(result.getResponse().getContentAsString()).get("errors");
		assertThat(errors.get("email").asText()).contains("Email");
		assertThat(errors.get("password").asText()).contains("Mật khẩu");
	}

	@Test
	void loginBackfillsLocalRowAndRejectsWrongPasswordOrLockedAccount() throws Exception {
		// Tạo thẳng trên Keycloak (như user có sẵn trong realm): chưa có trong auth_user
		String id = users.register("login@example.com");
		assertThat(userAccountRepository.findByUserId(id)).isEmpty();

		JsonNode tokens = ok(post("/api/auth/login"), null, "{\"email\":\"Login@Example.com\",\"password\":\"secret123\"}");
		assertThat(ok(get("/api/auth/me"), tokens.get("accessToken").asText(), null).get("id").asText()).isEqualTo(id);
		assertThat(userAccountRepository.findByUserId(id)).isPresent();

		assertThat(status(post("/api/auth/login"), null, "{\"email\":\"login@example.com\",\"password\":\"wrong\"}"))
				.isEqualTo(401);

		ok(put("/api/auth/users/{id}", id), admin, "{\"status\":\"INACTIVE\"}");
		assertThat(userAccountRepository.findByUserId(id).orElseThrow().isEnabled()).isFalse();
		assertThat(status(post("/api/auth/login"), null, "{\"email\":\"login@example.com\",\"password\":\"secret123\"}"))
				.isEqualTo(403);
	}

	@Test
	void refreshTokenWorksOnceAndLogoutEndsSession() throws Exception {
		users.register("refresh@example.com");
		String login = "{\"email\":\"refresh@example.com\",\"password\":\"secret123\"}";
		String first = ok(post("/api/auth/login"), null, login).get("refreshToken").asText();

		String second = ok(post("/api/auth/refresh"), null, "{\"refreshToken\":\"%s\"}".formatted(first))
				.get("refreshToken").asText();
		assertThat(status(post("/api/auth/refresh"), null, "{\"refreshToken\":\"%s\"}".formatted(first))).isEqualTo(401);

		assertThat(status(post("/api/auth/logout"), null, "{\"refreshToken\":\"%s\"}".formatted(second))).isEqualTo(204);
		assertThat(status(post("/api/auth/refresh"), null, "{\"refreshToken\":\"%s\"}".formatted(second))).isEqualTo(401);
	}

	@Test
	void adminCreatedUserIsCopiedToLocalTable() throws Exception {
		JsonNode created = ok(post("/api/auth/users"), admin,
				"{\"email\":\"copy@example.com\",\"password\":\"secret123\",\"fullName\":\"Copy\"}");
		assertThat(userAccountRepository.findByUserId(created.get("id").asText()).orElseThrow().getFullName())
				.isEqualTo("Copy");
	}

	private String roles(String token) throws Exception {
		return ok(get("/api/auth/me"), token, null).get("roles").toString();
	}

	private JsonNode ok(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		MvcResult result = exec(request, token, body);
		assertThat(result.getResponse().getStatus())
				.as(result.getResponse().getContentAsString())
				.isBetween(200, 201);
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private int status(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		return exec(request, token, body).getResponse().getStatus();
	}

	private MvcResult exec(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(request).andReturn();
	}
}
