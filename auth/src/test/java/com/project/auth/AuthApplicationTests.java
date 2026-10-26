package com.project.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void registerLoginAndCallMe() throws Exception {
		MvcResult registered = exec(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"An@Example.com\",\"password\":\"secret123\"}"));
		assertThat(registered.getResponse().getStatus()).isEqualTo(201);
		assertThat(json(registered).get("roles").toString()).isEqualTo("[\"USER\"]");

		JsonNode tokens = login("an@example.com", "secret123");
		assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
		assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);

		MvcResult me = exec(get("/api/auth/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()));
		assertThat(me.getResponse().getStatus()).isEqualTo(200);
		assertThat(json(me).get("email").asText()).isEqualTo("an@example.com");

		assertThat(exec(get("/api/auth/me")).getResponse().getStatus()).isEqualTo(401);
		assertThat(exec(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt")).getResponse().getStatus())
				.isEqualTo(401);
	}

	@Test
	void registerValidationAndDuplicates() throws Exception {
		register("dup@example.com", "secret123");
		assertThat(registerStatus("DUP@example.com", "secret123")).isEqualTo(409);
		assertThat(registerStatus("weak@example.com", "short1")).isEqualTo(400);
		assertThat(registerStatus("weak@example.com", "onlyletters")).isEqualTo(400);
		assertThat(registerStatus("not-an-email", "secret123")).isEqualTo(400);
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveSameError() throws Exception {
		register("pw@example.com", "secret123");
		MvcResult wrongPassword = loginRaw("pw@example.com", "wrong1234");
		MvcResult unknownEmail = loginRaw("nobody@example.com", "secret123");
		assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
		assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
		assertThat(json(wrongPassword).get("detail")).isEqualTo(json(unknownEmail).get("detail"));
	}

	@Test
	void refreshRotatesTokenAndReuseRevokesAllSessions() throws Exception {
		register("rot@example.com", "secret123");
		String first = login("rot@example.com", "secret123").get("refreshToken").asText();
		String otherSession = login("rot@example.com", "secret123").get("refreshToken").asText();

		MvcResult rotated = refresh(first);
		assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
		String second = json(rotated).get("refreshToken").asText();
		assertThat(second).isNotEqualTo(first);

		// Dùng lại token cũ -> bị coi là đánh cắp: từ chối và thu hồi mọi phiên của user
		assertThat(refresh(first).getResponse().getStatus()).isEqualTo(401);
		assertThat(refresh(second).getResponse().getStatus()).isEqualTo(401);
		assertThat(refresh(otherSession).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		register("out@example.com", "secret123");
		String refreshToken = login("out@example.com", "secret123").get("refreshToken").asText();

		assertThat(exec(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}")).getResponse().getStatus()).isEqualTo(204);
		assertThat(refresh(refreshToken).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void concurrentRefreshWithSameToken_onlyOneWins() throws Exception {
		register("race@example.com", "secret123");
		String refreshToken = login("race@example.com", "secret123").get("refreshToken").asText();

		List<Integer> statuses = runConcurrently(8, () -> refresh(refreshToken).getResponse().getStatus());

		assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
		assertThat(statuses).filteredOn(s -> s != 200).containsOnly(401);
	}

	@Test
	void concurrentRegistrationsWithSameEmail_onlyOneWins() throws Exception {
		List<Integer> statuses = runConcurrently(8, () -> registerStatus("same@example.com", "secret123"));

		assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
		assertThat(statuses).filteredOn(s -> s != 201).containsOnly(409);
	}

	@Test
	void onlyAdminCanChangeRoles() throws Exception {
		String userId = json(register("promote@example.com", "secret123")).get("id").asText();
		String userToken = login("promote@example.com", "secret123").get("accessToken").asText();
		String adminToken = login("admin@example.com", "Admin@123").get("accessToken").asText();
		String body = "{\"roles\":[\"ADMIN\"]}";

		assertThat(exec(put("/api/auth/users/{id}/roles", userId).header("Authorization", "Bearer " + userToken)
				.contentType(MediaType.APPLICATION_JSON).content(body)).getResponse().getStatus()).isEqualTo(403);

		MvcResult promoted = exec(put("/api/auth/users/{id}/roles", userId)
				.header("Authorization", "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON).content(body));
		assertThat(promoted.getResponse().getStatus()).isEqualTo(200);
		assertThat(json(promoted).get("roles").toString()).isEqualTo("[\"ADMIN\",\"USER\"]");

		JsonNode newTokens = login("promote@example.com", "secret123");
		Jwt jwt = jwksDecoder().decode(newTokens.get("accessToken").asText());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
	}

	@Test
	void jwksExposesOnlyPublicKeyThatVerifiesIssuedTokens() throws Exception {
		register("jwks@example.com", "secret123");
		String accessToken = login("jwks@example.com", "secret123").get("accessToken").asText();

		String jwks = exec(get("/api/auth/jwks")).getResponse().getContentAsString();
		RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);
		assertThat(key.isPrivate()).isFalse();

		Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(accessToken);
		assertThat(jwt.getIssuer().toString()).isEqualTo("http://auth-service");
		assertThat(jwt.getClaimAsString("email")).isEqualTo("jwks@example.com");
		// sub = id user (UUID v7)
		assertThat(java.util.UUID.fromString(jwt.getSubject()).version()).isEqualTo(7);
	}

	private JwtDecoder jwksDecoder() throws Exception {
		String jwks = exec(get("/api/auth/jwks")).getResponse().getContentAsString();
		return NimbusJwtDecoder.withPublicKey(((RSAKey) JWKSet.parse(jwks).getKeys().get(0)).toRSAPublicKey()).build();
	}

	private MvcResult register(String email, String password) throws Exception {
		MvcResult result = exec(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		return result;
	}

	private int registerStatus(String email, String password) throws Exception {
		return exec(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
				.getResponse().getStatus();
	}

	private JsonNode login(String email, String password) throws Exception {
		MvcResult result = loginRaw(email, password);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		return json(result);
	}

	private MvcResult loginRaw(String email, String password) throws Exception {
		return exec(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private MvcResult refresh(String refreshToken) throws Exception {
		return exec(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private MvcResult exec(RequestBuilder request) throws Exception {
		return mvc.perform(request).andReturn();
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private interface ThrowingSupplier<T> {
		T get() throws Exception;
	}

	private static <T> List<T> runConcurrently(int threads, ThrowingSupplier<T> call) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return call.get();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get());
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}
}
