package com.project.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.project.auth.entity.Role;
import com.project.auth.repository.RoleRepository;
import com.project.auth.service.AuthorizationBootstrap;

/** API phân quyền: thao tác, menu, role, ma trận role × menu × thao tác; quyền trong JWT; menu của user. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	AuthorizationBootstrap bootstrap;

	String admin;

	@BeforeEach
	void loginAdmin() throws Exception {
		admin = login("admin@example.com", "Admin@123").get("accessToken").asText();
	}

	@Test
	void seededPermissionsMenusAndAdminTokenHasEveryCell() throws Exception {
		List<String> codes = new ArrayList<>();
		for (JsonNode p : ok(get("/api/auth/permissions"), admin)) {
			codes.add(p.get("code").asText());
			if (Set.of("VIEW", "ADD", "EDIT", "DELETE", "EXPORT").contains(p.get("code").asText())) {
				assertThat(p.get("type").asText()).isEqualTo("SYSTEM");
			}
		}
		assertThat(codes).contains("VIEW", "ADD", "EDIT", "DELETE", "EXPORT");

		JsonNode system = menu(ok(get("/api/auth/menus"), admin), "system");
		assertThat(system.get("system").asBoolean()).isTrue();
		List<String> children = new ArrayList<>();
		system.get("children").forEach(c -> children.add(c.get("code").asText()));
		assertThat(children).containsExactly("user", "role", "permission", "menu");
		assertThat(system.get("children").get(0).get("level").asInt()).isEqualTo(2);

		assertThat(decode(admin).getClaimAsStringList("permissions"))
				.contains("category:ADD", "customer:VIEW", "audit:VIEW", "role:DELETE", "menu:EXPORT");
	}

	@Test
	void roleGrantsMatrixCellsInTokenAndMenusInAuthorities() throws Exception {
		String userMenu = menu(ok(get("/api/auth/menus"), admin), "system").get("children").get(0).get("id").asText();
		JsonNode role = ok(post("/api/auth/roles"), admin, """
				{"code":"EDITOR","name":"Biên tập","permissions":["category:ADD","category:EDIT"],
				 "menuIds":["%s"]}""".formatted(userMenu));
		assertThat(role.get("system").asBoolean()).isFalse();
		assertThat(role.get("status").asText()).isEqualTo("ACTIVE");

		String userId = register("editor@example.com");
		ok(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"EDITOR\"]}");
		String token = login("editor@example.com", "secret123").get("accessToken").asText();
		assertThat(decode(token).getClaimAsStringList("permissions")).containsExactly("category:ADD", "category:EDIT");

		// Chỉ gán menu con: menu cha được thêm vào để cây không bị đứt
		JsonNode authorities = ok(get("/api/auth/me/authorities"), token);
		assertThat(authorities.get("roles").toString()).isEqualTo("[\"EDITOR\",\"USER\"]");
		JsonNode menus = authorities.get("menus");
		assertThat(menus).hasSize(1);
		assertThat(menus.get(0).get("code").asText()).isEqualTo("system");
		assertThat(menus.get(0).get("children").get(0).get("url").asText()).isEqualTo("/admin/users");

		assertThat(status(get("/api/auth/roles"), token, null)).isEqualTo(403);
	}

	@Test
	void matrixRowSaveChecksVersionAndUnknownCells() throws Exception {
		JsonNode role = ok(post("/api/auth/roles"), admin, "{\"code\":\"MATRIX\",\"name\":\"Ma trận\"}");
		String id = role.get("id").asText();
		long version = role.get("version").asLong();

		assertThat(status(put("/api/auth/roles/{id}/permissions", id), admin,
				"{\"permissions\":[\"category:FLY\"],\"version\":%d}".formatted(version))).isEqualTo(400);
		JsonNode saved = ok(put("/api/auth/roles/{id}/permissions", id), admin,
				"{\"permissions\":[\"customer:VIEW\",\"audit:EXPORT\"],\"version\":%d}".formatted(version));
		assertThat(saved.get("permissions").toString()).isEqualTo("[\"audit:EXPORT\",\"customer:VIEW\"]");
		// Gửi version cũ -> 409
		assertThat(status(put("/api/auth/roles/{id}/permissions", id), admin,
				"{\"permissions\":[],\"version\":%d}".formatted(version))).isEqualTo(409);
	}

	@Test
	void inactiveRoleGrantsNothing() throws Exception {
		JsonNode role = ok(post("/api/auth/roles"), admin,
				"{\"code\":\"PAUSED\",\"name\":\"Tạm dừng\",\"permissions\":[\"customer:VIEW\"]}");
		String userId = register("paused@example.com");
		ok(put("/api/auth/users/{id}/roles", userId), admin, "{\"roles\":[\"PAUSED\"]}");
		ok(put("/api/auth/roles/{id}", role.get("id").asText()), admin,
				"{\"name\":\"Tạm dừng\",\"status\":\"INACTIVE\",\"version\":%d}".formatted(role.get("version").asLong()));

		String token = login("paused@example.com", "secret123").get("accessToken").asText();
		assertThat(decode(token).getClaimAsStringList("permissions")).isEmpty();

		// Role hệ thống không tắt / không xoá được
		JsonNode userRole = byCode(ok(get("/api/auth/roles"), admin), "USER");
		assertThat(status(put("/api/auth/roles/{id}", userRole.get("id").asText()), admin,
				"{\"name\":\"x\",\"status\":\"INACTIVE\",\"version\":%d}".formatted(userRole.get("version").asLong())))
				.isEqualTo(400);
		assertThat(status(delete("/api/auth/roles/{id}", userRole.get("id").asText()), admin, null)).isEqualTo(400);
	}

	@Test
	void roleMembersCanBeEditedFromRoleButOnlyAdminTouchesAdminRole() throws Exception {
		ok(post("/api/auth/roles"), admin, """
				{"code":"ROLE_MANAGER","name":"Quản lý vai trò",
				 "permissions":["role:VIEW","role:EDIT","user:VIEW","user:EDIT"]}""");
		String managerId = register("rolemanager@example.com");
		ok(put("/api/auth/users/{id}/roles", managerId), admin, "{\"roles\":[\"ROLE_MANAGER\"]}");
		String manager = login("rolemanager@example.com", "secret123").get("accessToken").asText();

		JsonNode team = ok(post("/api/auth/roles"), admin, "{\"code\":\"TEAM\",\"name\":\"Nhóm\"}");
		String memberId = register("member@example.com");
		JsonNode updated = ok(put("/api/auth/roles/{id}", team.get("id").asText()), manager,
				"{\"name\":\"Nhóm\",\"userIds\":[\"%s\"],\"version\":%d}".formatted(memberId, team.get("version").asLong()));
		assertThat(updated.get("userIds").toString()).contains(memberId);
		assertThat(ok(get("/api/auth/roles/{id}", team.get("id").asText()), admin).get("userIds")).hasSize(1);

		JsonNode adminRole = byCode(ok(get("/api/auth/roles"), admin), "ADMIN");
		assertThat(status(put("/api/auth/roles/{id}", adminRole.get("id").asText()), manager,
				"{\"name\":\"Admin\",\"userIds\":[\"%s\"],\"version\":%d}"
						.formatted(managerId, adminRole.get("version").asLong()))).isEqualTo(403);

		// Xoá role đang có thành viên: gỡ role khỏi user
		assertThat(status(delete("/api/auth/roles/{id}", team.get("id").asText()), admin, null)).isEqualTo(204);
		assertThat(ok(get("/api/auth/users?keyword=member@"), admin).get("content").get(0).get("roles").toString())
				.isEqualTo("[\"USER\"]");
	}

	@Test
	void deletingCustomPermissionRevokesItFromRoles() throws Exception {
		JsonNode approve = ok(post("/api/auth/permissions"), admin, "{\"code\":\"APPROVE\",\"name\":\"Duyệt\"}");
		assertThat(approve.get("type").asText()).isEqualTo("CUSTOM");
		JsonNode role = ok(post("/api/auth/roles"), admin,
				"{\"code\":\"APPROVER\",\"name\":\"Duyệt\",\"permissions\":[\"business:APPROVE\",\"business:VIEW\"]}");

		assertThat(status(delete("/api/auth/permissions/{id}", approve.get("id").asText()), admin, null)).isEqualTo(204);
		assertThat(ok(get("/api/auth/roles/{id}", role.get("id").asText()), admin).get("permissions").toString())
				.isEqualTo("[\"business:VIEW\"]");

		JsonNode view = byCode(ok(get("/api/auth/permissions"), admin), "VIEW");
		assertThat(status(delete("/api/auth/permissions/{id}", view.get("id").asText()), admin, null)).isEqualTo(400);
	}

	@Test
	void deletingMenuDeletesSubtreeAndRevokesGrants() throws Exception {
		JsonNode parent = ok(post("/api/auth/menus"), admin, "{\"code\":\"report\",\"name\":\"Báo cáo\"}");
		JsonNode child = ok(post("/api/auth/menus"), admin,
				"{\"code\":\"report-daily\",\"name\":\"Hằng ngày\",\"url\":\"/reports/daily\",\"parentId\":\"%s\"}"
						.formatted(parent.get("id").asText()));
		assertThat(child.get("level").asInt()).isEqualTo(2);
		assertThat(child.get("orderIndex").asInt()).isEqualTo(1);

		JsonNode role = ok(post("/api/auth/roles"), admin, """
				{"code":"REPORTER","name":"Báo cáo","permissions":["report-daily:VIEW","category:VIEW"],
				 "menuIds":["%s"]}""".formatted(child.get("id").asText()));

		// Menu cha không được là menu con của chính nó
		assertThat(status(put("/api/auth/menus/{id}", parent.get("id").asText()), admin,
				"{\"name\":\"Báo cáo\",\"parentId\":\"%s\",\"version\":%d}"
						.formatted(child.get("id").asText(), parent.get("version").asLong()))).isEqualTo(400);

		assertThat(status(delete("/api/auth/menus/{id}", parent.get("id").asText()), admin, null)).isEqualTo(204);
		JsonNode after = ok(get("/api/auth/roles/{id}", role.get("id").asText()), admin);
		assertThat(after.get("permissions").toString()).isEqualTo("[\"category:VIEW\"]");
		assertThat(after.get("menuIds")).isEmpty();
		assertThat(status(get("/api/auth/menus/{id}", child.get("id").asText()), admin, null)).isEqualTo(404);

		JsonNode system = menu(ok(get("/api/auth/menus"), admin), "system");
		assertThat(status(delete("/api/auth/menus/{id}", system.get("id").asText()), admin, null)).isEqualTo(400);
	}

	@Test
	void reorderMovesMenus() throws Exception {
		JsonNode a = ok(post("/api/auth/menus"), admin, "{\"code\":\"ord-a\",\"name\":\"A\",\"orderIndex\":1}");
		JsonNode b = ok(post("/api/auth/menus"), admin, "{\"code\":\"ord-b\",\"name\":\"B\",\"orderIndex\":2}");
		assertThat(status(put("/api/auth/menus/reorder"), admin, """
				[{"id":"%s","orderIndex":2},{"id":"%s","orderIndex":1,"parentId":"%s"}]"""
				.formatted(a.get("id").asText(), b.get("id").asText(), a.get("id").asText()))).isEqualTo(204);
		JsonNode movedA = ok(get("/api/auth/menus/{id}", a.get("id").asText()), admin);
		JsonNode movedB = ok(get("/api/auth/menus/{id}", b.get("id").asText()), admin);
		assertThat(movedA.get("orderIndex").asInt()).isEqualTo(2);
		assertThat(movedB.get("parentId").asText()).isEqualTo(a.get("id").asText());
		assertThat(movedB.get("level").asInt()).isEqualTo(2);
	}

	@Test
	void adminCreatesLocksAndSearchesUsers() throws Exception {
		JsonNode created = ok(post("/api/auth/users"), admin, """
				{"email":"staff@example.com","password":"secret123","fullName":"Nguyễn Văn Staff",
				 "phoneNumber":"0912345678","roles":["USER"]}""");
		assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(login("staff@example.com", "secret123").get("accessToken").asText()).isNotBlank();

		JsonNode found = ok(get("/api/auth/users?keyword=văn staff"), admin);
		assertThat(found.get("totalElements").asLong()).isEqualTo(1);

		ok(put("/api/auth/users/{id}", created.get("id").asText()), admin,
				"{\"fullName\":\"Nguyễn Văn Staff\",\"status\":\"INACTIVE\",\"version\":%d}"
						.formatted(created.get("version").asLong()));
		assertThat(exec(post("/api/auth/login"), null, "{\"email\":\"staff@example.com\",\"password\":\"secret123\"}")
				.getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void legacyGrantsAreMigratedOnStartup() throws Exception {
		Role legacy = new Role();
		legacy.setCode("LEGACY");
		legacy.setName("Bản cũ");
		legacy.setPermissions(new HashSet<>(Set.of("category:create", "resource:read", "customer:VIEW")));
		roleRepository.saveAndFlush(legacy);

		bootstrap.run(null);

		assertThat(roleRepository.findByCode("LEGACY").orElseThrow().getPermissions())
				.containsExactlyInAnyOrder("category:ADD", "permission:VIEW", "customer:VIEW");
	}

	private static JsonNode menu(JsonNode tree, String code) {
		return byCode(tree, code);
	}

	private static JsonNode byCode(JsonNode list, String code) {
		for (JsonNode node : list) {
			if (node.get("code").asText().equals(code)) {
				return node;
			}
		}
		throw new AssertionError("Không tìm thấy " + code);
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
