package com.luckybox.admin.banner;

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
class AdminBannerApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminBannerTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'Banner'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM banners
						WHERE title LIKE 'Admin Banner Test%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'admin-banner-user-%')
					OR entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-banner-user-%'
					)
				""");
		jdbcTemplate.update("DELETE FROM banners WHERE title LIKE 'Admin Banner Test%'");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-banner-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-banner-user-%'");
	}

	@Test
	void adminCreatesListsUpdatesAndPublishesBanner() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String token = UUID.randomUUID().toString().substring(0, 8);
		String title = "Admin Banner Test " + token;
		String imageUrl = "https://images.example.com/admin-banner-test-" + token + ".png";

		MvcResult createResult = mockMvc.perform(post("/api/admin/banners")
						.session(adminSession)
						.contentType("application/json")
						.content(bannerJson(title, imageUrl, "/news", "HOME_HERO", "ACTIVE")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.position").value("HOME_HERO"))
				.andExpect(jsonPath("$.positionLabel").value("首頁主視覺"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.statusLabel").value("啟用"))
				.andReturn();
		long bannerId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get("/api/admin/banners")
						.session(adminSession)
						.param("status", "ACTIVE")
						.param("position", "HOME_HERO")
						.param("q", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) bannerId))
				.andExpect(jsonPath("$[0].imageUrl").value(imageUrl));

		MvcResult publicListResult = mockMvc.perform(get("/api/banners")
						.param("position", "HOME_HERO"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(publicListResult.getResponse().getContentAsString()).contains(title);

		mockMvc.perform(patch("/api/admin/banners/{bannerId}", bannerId)
						.session(adminSession)
						.contentType("application/json")
						.content(bannerJson(title, imageUrl, "/news", "HOME_HERO", "ARCHIVED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) bannerId))
				.andExpect(jsonPath("$.status").value("ARCHIVED"));

		MvcResult archivedPublicListResult = mockMvc.perform(get("/api/banners")
						.param("position", "HOME_HERO"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(archivedPublicListResult.getResponse().getContentAsString()).doesNotContain(title);

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE entity_type = 'Banner'
					AND entity_id = ?
					AND action IN ('ADMIN_BANNER_CREATED', 'ADMIN_BANNER_UPDATED')
				""", Integer.class, String.valueOf(bannerId));
		assertThat(auditCount).isEqualTo(2);
	}

	@Test
	void normalUserCannotUseAdminBannerApi() throws Exception {
		MockHttpSession userSession = registerUser();
		mockMvc.perform(get("/api/admin/banners").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsInvalidBannerPayload() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String title = "Admin Banner Test Invalid";

		mockMvc.perform(post("/api/admin/banners")
						.session(adminSession)
						.contentType("application/json")
						.content(bannerJson(title, "not-a-url", "/news", "HOME_HERO", "ACTIVE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_BANNER_IMAGE_URL"));

		mockMvc.perform(post("/api/admin/banners")
						.session(adminSession)
						.contentType("application/json")
						.content(bannerJson(title, "https://images.example.com/banner.png", "/news", "SIDE_BAR", "ACTIVE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_BANNER_POSITION"));

		mockMvc.perform(post("/api/admin/banners")
						.session(adminSession)
						.contentType("application/json")
						.content(bannerJson(title, "https://images.example.com/banner.png", "/news", "HOME_HERO", "PUBLISHED")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_BANNER_STATUS"));
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
		String email = "admin-banner-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Banner 管理一般會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static String bannerJson(String title, String imageUrl, String href, String position, String status) {
		return """
				{
				  "title": "%s",
				  "imageUrl": "%s",
				  "href": "%s",
				  "position": "%s",
				  "status": "%s"
				}
				""".formatted(title, imageUrl, href, position, status);
	}
}
