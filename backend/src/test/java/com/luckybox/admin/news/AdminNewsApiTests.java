package com.luckybox.admin.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNewsApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminNewsTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'News'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM news
						WHERE slug LIKE 'admin-news-test-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'admin-news-user-%')
					OR entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-news-user-%'
					)
				""");
		jdbcTemplate.update("DELETE FROM news WHERE slug LIKE 'admin-news-test-%'");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-news-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-news-user-%'");
	}

	@Test
	void adminCreatesListsUpdatesAndPublishesNews() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admin-news-test-" + UUID.randomUUID().toString().substring(0, 8);

		MvcResult createResult = mockMvc.perform(post("/api/admin/news")
						.session(adminSession)
						.contentType("application/json")
						.content(newsJson(slug, "公告管理測試", "PUBLISHED", "")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.slug").value(slug))
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.statusLabel").value("已發布"))
				.andExpect(jsonPath("$.publishedAt").isNotEmpty())
				.andReturn();
		long newsId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get("/api/admin/news")
						.session(adminSession)
						.param("status", "PUBLISHED")
						.param("q", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) newsId))
				.andExpect(jsonPath("$[0].title").value("公告管理測試"));

		MvcResult publicListResult = mockMvc.perform(get("/api/news"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(publicListResult.getResponse().getContentAsString()).contains(slug);

		mockMvc.perform(get("/api/news/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(slug))
				.andExpect(jsonPath("$.content").value("公告管理測試內容"));

		mockMvc.perform(patch("/api/admin/news/{newsId}", newsId)
						.session(adminSession)
						.contentType("application/json")
						.content(newsJson(slug, "公告管理測試更新", "ARCHIVED", "")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) newsId))
				.andExpect(jsonPath("$.status").value("ARCHIVED"))
				.andExpect(jsonPath("$.publishedAt").doesNotExist());

		mockMvc.perform(get("/api/news/{slug}", slug))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NEWS_NOT_FOUND"));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE entity_type = 'News'
					AND entity_id = ?
					AND action IN ('ADMIN_NEWS_CREATED', 'ADMIN_NEWS_UPDATED')
				""", Integer.class, String.valueOf(newsId));
		assertThat(auditCount).isEqualTo(2);
	}

	@Test
	void normalUserCannotUseAdminNewsApi() throws Exception {
		MockHttpSession userSession = registerUser();
		mockMvc.perform(get("/api/admin/news").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsInvalidNewsPayload() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admin-news-test-" + UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(post("/api/admin/news")
						.session(adminSession)
						.contentType("application/json")
						.content(newsJson(slug, "壞狀態公告", "LIVE", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_NEWS_STATUS"));

		mockMvc.perform(post("/api/admin/news")
						.session(adminSession)
						.contentType("application/json")
						.content(newsJson(slug, "壞時間公告", "PUBLISHED", "not-a-time")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_NEWS_PUBLISHED_AT"));
	}

	@Test
	void duplicateSlugReturnsConflict() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admin-news-test-" + UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(post("/api/admin/news")
						.session(adminSession)
						.contentType("application/json")
						.content(newsJson(slug, "第一則公告", "DRAFT", "")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/admin/news")
						.session(adminSession)
						.contentType("application/json")
						.content(newsJson(slug, "第二則公告", "DRAFT", "")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NEWS_SLUG_EXISTS"));
	}

	private MockHttpSession loginAdmin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"admin@luckybox.local","password":"ChangeMe123!"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "admin-news-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "公告管理一般會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static String newsJson(String slug, String title, String status, String publishedAt) {
		return """
				{
				  "title": "%s",
				  "slug": "%s",
				  "content": "公告管理測試內容",
				  "status": "%s",
				  "publishedAt": "%s"
				}
				""".formatted(title, slug, status, publishedAt);
	}
}
