package com.project.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

/** API phân quyền: action, resource, role, menu; quyền trong JWT; menu của user. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	String admin;

	@BeforeEach
	void loginAdmin() throws Exception {
		admin = login("admin@example.com", "Admin@123").get("accessToken").asText();
	}

	@Test
	void seededPermissionsAndAdminTokenHasAll() throws Exception {
		JsonNode resources = ok(get("/api/auth/resources"), admin);
		List<String> codes = new ArrayList<>();
		resources.forEach(r -> codes.add(r.get("code").asText()));
		assertThat(codes).contains("category", "customer", "business", "audit", "user", "role", "menu");

		Jwt jwt = decode(admin);
		assertThat(jwt.getClaimAsStringList("permissions"))
				.contains("category:create", "customer:read", "audit:read", "role:delete");
	}

	@Test
	void roleGrantsPermissionsInTokenAndMenusInAuthorities() throws Exception {
		String parent = ok(post("/api/auth/menus"), admin,
				"{\"code\":\"m-catalog\",\"name\":\"Danh mục\",\"sortOrder\":1,\"active\":true}").get("id").asText();
		String child = ok(post("/api/auth/menus"), admin,
				"{\"code\":\"m-catalog-list\",\"name\":\"Danh sách\",\"path\":\"/categories\",\"parentId\":\"%s\",\"sortOrder\":1,\"active\":true}"
						.formatted(parent)).get("id").asText();
		ok(post("/api/auth/menus"), admin, "{\"code\":\"m-hidden\",\"name\":\"Ẩn\",\"sortOrder\":2,\"active\":true}");

		JsonNode role = ok(post("/api/auth/roles"), admin, """
				{"code":"EDITOR","name":"Biên tập","permissions":["category:create","category:update"],
				 "menuIds":["%s"]}""".formatted(child));
		assertThat(role.get("builtIn").asBoolean()).isFalse();

		String userId = register("editor@example.com");
		ok(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"EDITOR\"]}");

		String token = login("editor@example.com", "secret123").get("accessToken").asText();
		assertThat(decode(token).getClaimAsStringList("permissions"))
				.containsExactly("category:create", "category:update");

		// Chỉ gán menu con: menu cha được thêm vào để cây không bị đứt, menu không được gán thì không thấy
		JsonNode authorities = ok(get("/api/auth/me/authorities"), token);
		assertThat(authorities.get("roles").toString()).isEqualTo("[\"EDITOR\",\"USER\"]");
		JsonNode menus = authorities.get("menus");
		assertThat(menus).hasSize(1);
		assertThat(menus.get(0).get("code").asText()).isEqualTo("m-catalog");
		assertThat(menus.get(0).get("children").get(0).get("path").asText()).isEqualTo("/categories");

		// Không có quyền role:read
		assertThat(status(get("/api/auth/roles"), token, null)).isEqualTo(403);
	}

	@Test
	void unknownPermissionMenuOrRoleIsRejected() throws Exception {
		assertThat(status(post("/api/auth/roles"), admin,
				"{\"code\":\"BAD\",\"name\":\"x\",\"permissions\":[\"category:fly\"],\"menuIds\":[]}")).isEqualTo(400);
		assertThat(status(post("/api/auth/roles"), admin,
				"{\"code\":\"bad-code\",\"name\":\"x\",\"permissions\":[],\"menuIds\":[]}")).isEqualTo(400);
		String userId = register("unknown-role@example.com");
		assertThat(status(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"NOPE\"]}"))
				.isEqualTo(400);
	}

	@Test
	void onlyAdminCanGrantAdminRole() throws Exception {
		ok(post("/api/auth/roles"), admin,
				"{\"code\":\"USER_MANAGER\",\"name\":\"Quản lý user\",\"permissions\":[\"user:read\",\"user:update\"],\"menuIds\":[]}");
		String managerId = register("manager@example.com");
		ok(put("/api/auth/users/{id}/roles", managerId), admin, "{\"roles\":[\"USER_MANAGER\"]}");
		String manager = login("manager@example.com", "secret123").get("accessToken").asText();

		String targetId = register("target@example.com");
		assertThat(status(put("/api/auth/users/{id}/roles", targetId), manager, "{\"roles\":[\"USER_MANAGER\"]}"))
				.isEqualTo(200);
		assertThat(status(put("/api/auth/users/{id}/roles", managerId), manager, "{\"roles\":[\"ADMIN\"]}"))
				.isEqualTo(403);
		assertThat(ok(get("/api/auth/users?size=100"), manager).get("totalElements").asLong()).isPositive();
	}

	@Test
	void deletingRoleChecksBuiltInAndUsage() throws Exception {
		JsonNode roles = ok(get("/api/auth/roles"), admin);
		String userRoleId = null;
		for (JsonNode r : roles) {
			if (r.get("code").asText().equals("USER")) {
				userRoleId = r.get("id").asText();
			}
		}
		assertThat(status(delete("/api/auth/roles/{id}", userRoleId), admin, null)).isEqualTo(400);

		String roleId = ok(post("/api/auth/roles"), admin,
				"{\"code\":\"TEMP\",\"name\":\"Tạm\",\"permissions\":[],\"menuIds\":[]}").get("id").asText();
		String userId = register("temp@example.com");
		ok(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"TEMP\"]}");
		MvcResult inUse = exec(delete("/api/auth/roles/{id}", roleId), admin, null);
		assertThat(inUse.getResponse().getStatus()).isEqualTo(409);
		assertThat(json(inUse).get("code").asText()).isEqualTo("IN_USE");

		ok(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"USER\"]}");
		assertThat(status(delete("/api/auth/roles/{id}", roleId), admin, null)).isEqualTo(204);
	}

	@Test
	void removingActionFromResourceRevokesItFromRoles() throws Exception {
		ok(post("/api/auth/actions"), admin, "{\"code\":\"export\",\"name\":\"Xuất file\"}");
		JsonNode report = ok(post("/api/auth/resources"), admin,
				"{\"code\":\"report\",\"name\":\"Báo cáo\",\"actions\":[\"read\",\"export\"]}");
		assertThat(report.get("permissions").toString()).isEqualTo("[\"report:export\",\"report:read\"]");
		JsonNode role = ok(post("/api/auth/roles"), admin,
				"{\"code\":\"REPORTER\",\"name\":\"Báo cáo\",\"permissions\":[\"report:read\",\"report:export\"],\"menuIds\":[]}");

		// Action đang được resource dùng thì không xoá được
		String exportId = null;
		for (JsonNode a : ok(get("/api/auth/actions"), admin)) {
			if (a.get("code").asText().equals("export")) {
				exportId = a.get("id").asText();
			}
		}
		assertThat(status(delete("/api/auth/actions/{id}", exportId), admin, null)).isEqualTo(409);

		ok(put("/api/auth/resources/{id}", report.get("id").asText()), admin,
				"{\"name\":\"Báo cáo\",\"actions\":[\"read\"],\"version\":%d}".formatted(report.get("version").asLong()));
		JsonNode updatedRole = ok(get("/api/auth/roles/{id}", role.get("id").asText()), admin);
		assertThat(updatedRole.get("permissions").toString()).isEqualTo("[\"report:read\"]");

		assertThat(status(delete("/api/auth/actions/{id}", exportId), admin, null)).isEqualTo(204);
	}

	@Test
	void menuRejectsCycleAndDeleteWithChildren() throws Exception {
		JsonNode a = ok(post("/api/auth/menus"), admin, "{\"code\":\"cyc-a\",\"name\":\"A\",\"sortOrder\":0,\"active\":true}");
		JsonNode b = ok(post("/api/auth/menus"), admin,
				"{\"code\":\"cyc-b\",\"name\":\"B\",\"parentId\":\"%s\",\"sortOrder\":0,\"active\":true}"
						.formatted(a.get("id").asText()));

		assertThat(status(put("/api/auth/menus/{id}", a.get("id").asText()), admin,
				"{\"name\":\"A\",\"parentId\":\"%s\",\"sortOrder\":0,\"active\":true,\"version\":%d}"
						.formatted(b.get("id").asText(), a.get("version").asLong()))).isEqualTo(400);
		assertThat(status(delete("/api/auth/menus/{id}", a.get("id").asText()), admin, null)).isEqualTo(409);

		// Sửa bằng version cũ -> 409
		ok(put("/api/auth/menus/{id}", b.get("id").asText()), admin,
				"{\"name\":\"B2\",\"sortOrder\":0,\"active\":true,\"version\":%d}".formatted(b.get("version").asLong()));
		assertThat(status(put("/api/auth/menus/{id}", b.get("id").asText()), admin,
				"{\"name\":\"B3\",\"sortOrder\":0,\"active\":true,\"version\":%d}".formatted(b.get("version").asLong())))
				.isEqualTo(409);
	}

	private String register(String email) throws Exception {
		return ok(post("/api/auth/register"), null,
				"{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email)).get("id").asText();
	}

	private JsonNode login(String email, String password) throws Exception {
		return ok(post("/api/auth/login"), null,
				"{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
	}

	private Jwt decode(String token) throws Exception {
		String jwks = mvc.perform(get("/api/auth/jwks")).andReturn().getResponse().getContentAsString();
		return NimbusJwtDecoder.withPublicKey(((RSAKey) JWKSet.parse(jwks).getKeys().get(0)).toRSAPublicKey())
				.build().decode(token);
	}

	private JsonNode ok(MockHttpServletRequestBuilder request, String token) throws Exception {
		return ok(request, token, null);
	}

	private JsonNode ok(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
		MvcResult result = exec(request, token, body);
		assertThat(result.getResponse().getStatus())
				.as(result.getResponse().getContentAsString())
				.isBetween(200, 201);
		return json(result);
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

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
