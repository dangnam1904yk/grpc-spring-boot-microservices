package com.project.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.project.common.audit.test.CapturingAuditSink;
import com.project.common.security.Permissions;
import com.project.common.security.test.TestJwt;

/**
 * Bộ khung danh mục ({@code catalog/}) qua REST: dùng danh mục mẫu đơn vị tính và category.
 * DB H2 dùng chung giữa các test nên mỗi test dùng code riêng.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestJwt.Config.class, CapturingAuditSink.Config.class })
class CatalogApiTests {

	private static final String UNITS = "/api/category/catalogs/units";

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper objectMapper;

	final String admin = TestJwt.admin("catalog-admin");

	@Test
	void unitCrudWithExtraField() throws Exception {
		JsonNode created = json(201, post(UNITS),
				"{\"code\":\"KG\",\"name\":\"Kilogram\",\"symbol\":\"kg\",\"sortOrder\":2}", admin);
		assertThat(created.get("symbol").asText()).isEqualTo("kg");
		assertThat(created.get("active").asBoolean()).isTrue();
		String id = created.get("id").asText();

		// Đọc không cần token
		assertThat(json(200, get(UNITS + "/KG"), null, null).get("name").asText()).isEqualTo("Kilogram");

		// active, sortOrder để null = giữ nguyên; code trong body bị bỏ qua
		JsonNode updated = json(200, put(UNITS + "/" + id),
				"{\"code\":\"OTHER\",\"name\":\"Ki-lô-gam\",\"symbol\":\"kg\",\"version\":" + created.get("version") + "}",
				admin);
		assertThat(updated.get("code").asText()).isEqualTo("KG");
		assertThat(updated.get("name").asText()).isEqualTo("Ki-lô-gam");
		assertThat(updated.get("sortOrder").asInt()).isEqualTo(2);
		assertThat(updated.get("active").asBoolean()).isTrue();

		// Xoá bằng version cũ -> 409, đúng version -> 204
		assertThat(status(delete(UNITS + "/" + id).param("version", created.get("version").asText()), admin))
				.isEqualTo(409);
		assertThat(status(delete(UNITS + "/" + id).param("version", updated.get("version").asText()), admin))
				.isEqualTo(204);
		assertThat(status(get(UNITS + "/KG"), null)).isEqualTo(404);
	}

	@Test
	void unitErrors() throws Exception {
		json(201, post(UNITS), "{\"code\":\"BOX\",\"name\":\"Hộp\"}", admin);

		assertThat(status(post(UNITS), "{\"code\":\"BOX\",\"name\":\"Hộp 2\"}", admin)).isEqualTo(409);
		assertThat(status(post(UNITS), "{\"name\":\"Không có code\"}", admin)).isEqualTo(400);
		assertThat(status(post(UNITS), "{\"code\":\"NONAME\"}", admin)).isEqualTo(400);
		JsonNode box = json(200, get(UNITS + "/BOX"), null, null);
		assertThat(status(put(UNITS + "/" + box.get("id").asText()), "{\"name\":\"x\"}", admin)).isEqualTo(400);
		assertThat(status(put(UNITS + "/" + box.get("id").asText()), "{\"name\":\"x\",\"version\":99}", admin))
				.isEqualTo(409);
	}

	@Test
	void validationErrorsAreReturnedAsFieldMessageMap() throws Exception {
		// Annotation + kiểm tra chung + kiểm tra riêng của category gom về một lần
		JsonNode problem = json(400, post("/api/category"),
				"{\"name\":\"" + "x".repeat(256) + "\",\"parentCode\":\"NO_SUCH_PARENT\"}", admin);
		assertThat(problem.get("detail").asText()).isEqualTo("Dữ liệu không hợp lệ");
		assertThat(problem.get("code").asText()).isEqualTo("INVALID_ARGUMENT");
		assertThat(errors(problem)).containsOnly(
				Map.entry("name", "Quá 255 ký tự"),
				Map.entry("code", "Trường thông tin là bắt buộc"),
				Map.entry("parentCode", "Category cha không tồn tại"));

		json(201, post(UNITS), "{\"code\":\"VAL_DUP\",\"name\":\"Dup\"}", admin);
		assertThat(errors(json(409, post(UNITS), "{\"code\":\"VAL_DUP\",\"name\":\"Dup\"}", admin)))
				.containsOnly(Map.entry("code", "Đã tồn tại"));
		JsonNode dup = json(200, get(UNITS + "/VAL_DUP"), null, null);
		assertThat(errors(json(400, put(UNITS + "/" + dup.get("id").asText()), "{\"name\":\" \"}", admin)))
				.containsOnly(Map.entry("name", "Trường thông tin là bắt buộc"),
						Map.entry("version", "Trường thông tin là bắt buộc"));

		// Lỗi trước khi vào service: JSON sai kiểu, path / query param sai hoặc thiếu
		assertThat(errors(json(400, post(UNITS), "{\"code\":\"VAL_T\",\"name\":\"T\",\"sortOrder\":\"abc\"}", admin)))
				.containsOnly(Map.entry("sortOrder", "Sai kiểu dữ liệu"));
		assertThat(errors(json(400, put(UNITS + "/not-a-uuid"), "{\"name\":\"x\",\"version\":0}", admin)))
				.containsOnly(Map.entry("id", "Sai kiểu dữ liệu"));
		assertThat(errors(json(400, delete(UNITS + "/" + dup.get("id").asText()), null, admin)))
				.containsOnly(Map.entry("version", "Trường thông tin là bắt buộc"));
		assertThat(errors(json(400, get(UNITS).param("sort", "createBy"), null, null))).containsKey("sort");
	}

	@Test
	void messagesFollowAcceptLanguage() throws Exception {
		String body = "{\"name\":\"" + "x".repeat(256) + "\",\"parentCode\":\"NO_SUCH_PARENT\"}";

		JsonNode en = json(400, post("/api/category").header("Accept-Language", "en-US,en;q=0.9"), body, admin);
		assertThat(en.get("detail").asText()).isEqualTo("Invalid data");
		assertThat(errors(en)).containsOnly(
				Map.entry("name", "Must be at most 255 characters"),
				Map.entry("code", "This field is required"),
				Map.entry("parentCode", "Parent category does not exist"));

		// Tham số là message khác (tên danh mục) cũng được dịch
		assertThat(json(404, get(UNITS + "/NOPE").header("Accept-Language", "en"), null, null)
				.get("detail").asText()).isEqualTo("Unit code=NOPE not found");
		assertThat(json(404, get(UNITS + "/NOPE"), null, null).get("detail").asText())
				.isEqualTo("Đơn vị tính code=NOPE không tồn tại");
		// Ngôn ngữ không hỗ trợ -> tiếng Việt
		assertThat(json(404, get(UNITS + "/NOPE").header("Accept-Language", "fr"), null, null)
				.get("detail").asText()).isEqualTo("Đơn vị tính code=NOPE không tồn tại");

		// Lỗi của phần chung (trước khi vào service) và lỗi bảo mật cũng theo ngôn ngữ
		assertThat(errors(json(400, put(UNITS + "/not-a-uuid").header("Accept-Language", "en"),
				"{\"name\":\"x\",\"version\":0}", admin))).containsOnly(Map.entry("id", "Invalid data type"));
		assertThat(json(401, post(UNITS).header("Accept-Language", "en"), "{}", null).get("detail").asText())
				.isEqualTo("Not signed in, or the token is invalid / expired");
	}

	@Test
	void messageFilesHaveTheSameKeys() throws Exception {
		assertThat(keys("i18n/messages_en.properties")).isEqualTo(keys("i18n/messages_vi.properties"));
	}

	@Test
	void unitPermissionsAreSeparateFromCategory() throws Exception {
		String body = "{\"code\":\"PERM_U\",\"name\":\"Perm\"}";

		assertThat(status(post(UNITS), body, null)).isEqualTo(401);
		assertThat(status(post(UNITS), body, TestJwt.user("u-1"))).isEqualTo(403);
		assertThat(status(post(UNITS), body, TestJwt.withPermissions("u-2", Permissions.CATEGORY_CREATE)))
				.isEqualTo(403);
		JsonNode created = json(201, post(UNITS), body,
				TestJwt.withPermissions("u-3", Permissions.UNIT_CREATE));

		String deleter = TestJwt.withPermissions("u-4", Permissions.UNIT_UPDATE);
		assertThat(status(delete(UNITS + "/" + created.get("id").asText())
				.param("version", created.get("version").asText()), deleter)).isEqualTo(403);
	}

	@Test
	void unitSearchByKeywordActiveExtraFilterAndSort() throws Exception {
		json(201, post(UNITS), "{\"code\":\"SR_B\",\"name\":\"Search beta\",\"symbol\":\"sr1\"}", admin);
		json(201, post(UNITS), "{\"code\":\"SR_A\",\"name\":\"Search alpha\",\"symbol\":\"sr1\"}", admin);
		json(201, post(UNITS), "{\"code\":\"SR_C\",\"name\":\"Search gamma\",\"symbol\":\"sr2\",\"active\":false}",
				admin);
		// "_" trong keyword là ký tự thường, không phải wildcard của LIKE
		json(201, post(UNITS), "{\"code\":\"SRXA\",\"name\":\"Không khớp\"}", admin);

		assertThat(codes(get(UNITS).param("keyword", "sr_").param("sort", "code")))
				.containsExactly("SR_A", "SR_B", "SR_C");
		assertThat(codes(get(UNITS).param("keyword", "SEARCH").param("sort", "name,desc")))
				.containsExactly("SR_C", "SR_B", "SR_A");
		assertThat(codes(get(UNITS).param("keyword", "sr_").param("active", "false"))).containsExactly("SR_C");
		assertThat(codes(get(UNITS).param("symbol", "sr1").param("sort", "code")))
				.containsExactly("SR_A", "SR_B");
		assertThat(codes(get(UNITS).param("keyword", "sr_").param("sort", "symbol,desc")))
				.first().isEqualTo("SR_C");

		JsonNode page = json(200, get(UNITS).param("keyword", "sr_").param("size", "2"), null, null);
		assertThat(page.get("totalElements").asLong()).isEqualTo(3);
		assertThat(page.get("content")).hasSize(2);

		// Field không nằm trong whitelist / cú pháp sai -> 400
		assertThat(status(get(UNITS).param("sort", "createBy"), null)).isEqualTo(400);
		assertThat(status(get(UNITS).param("sort", "name,sideways"), null)).isEqualTo(400);
		assertThat(status(get(UNITS).param("sort", ","), null)).isEqualTo(400);
	}

	@Test
	void categoryKeepsItsApiAndAddsSearchAndDelete() throws Exception {
		JsonNode parent = json(201, post("/api/category"), "{\"code\":\"CT_PARENT\",\"name\":\"Cha\"}", admin);
		json(201, post("/api/category"), "{\"code\":\"CT_CHILD\",\"name\":\"Con\",\"parentCode\":\"CT_PARENT\"}",
				admin);

		assertThat(codes(get("/api/category").param("parentCode", "CT_PARENT"))).containsExactly("CT_CHILD");
		assertThat(codes(get("/api/category").param("keyword", "ct_"))).containsExactly("CT_PARENT", "CT_CHILD");

		// Đang có con -> không xoá được
		String version = parent.get("version").asText();
		String parentUrl = "/api/category/" + parent.get("id").asText();
		assertThat(status(delete(parentUrl).param("version", version), admin)).isEqualTo(409);
		assertThat(status(delete(parentUrl).param("version", version),
				TestJwt.withPermissions("u-5", Permissions.CATEGORY_UPDATE))).isEqualTo(403);

		JsonNode child = json(200, get("/api/category/CT_CHILD"), null, null);
		assertThat(status(delete("/api/category/" + child.get("id").asText())
				.param("version", child.get("version").asText()), admin)).isEqualTo(204);
		assertThat(status(delete(parentUrl).param("version", version), admin)).isEqualTo(204);
	}

	private static java.util.Set<Object> keys(String resource) throws Exception {
		java.util.Properties properties = new java.util.Properties();
		try (var in = CatalogApiTests.class.getClassLoader().getResourceAsStream(resource)) {
			assertThat(in).as(resource).isNotNull();
			properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
		}
		return properties.keySet();
	}

	private Map<String, String> errors(JsonNode problem) {
		Map<String, String> result = new LinkedHashMap<>();
		problem.get("errors").properties().forEach(e -> result.put(e.getKey(), e.getValue().asText()));
		return result;
	}

	private List<String> codes(MockHttpServletRequestBuilder request) throws Exception {
		List<String> result = new ArrayList<>();
		json(200, request, null, null).get("content").forEach(n -> result.add(n.get("code").asText()));
		return result;
	}

	private JsonNode json(int expectedStatus, MockHttpServletRequestBuilder request, String body, String token)
			throws Exception {
		MvcResult result = mvc.perform(prepare(request, body, token)).andReturn();
		assertThat(result.getResponse().getStatus())
				.as(result.getResponse().getContentAsString())
				.isEqualTo(expectedStatus);
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private int status(MockHttpServletRequestBuilder request, String token) throws Exception {
		return status(request, null, token);
	}

	private int status(MockHttpServletRequestBuilder request, String body, String token) throws Exception {
		return mvc.perform(prepare(request, body, token)).andReturn().getResponse().getStatus();
	}

	private static MockHttpServletRequestBuilder prepare(MockHttpServletRequestBuilder request, String body,
			String token) {
		if (token != null) {
			request.header("Authorization", TestJwt.bearer(token));
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return request;
	}
}
