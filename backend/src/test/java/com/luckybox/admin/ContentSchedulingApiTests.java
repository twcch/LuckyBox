package com.luckybox.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

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
class ContentSchedulingApiTests {

	private static final String FUTURE = Instant.now().plus(Duration.ofDays(1)).toString();
	private static final String PAST = Instant.now().minus(Duration.ofDays(1)).toString();
	private static final String PAST_EARLIER = Instant.now().minus(Duration.ofDays(2)).toString();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs WHERE entity_type = 'News'
					AND entity_id IN (SELECT CAST(id AS TEXT) FROM news WHERE slug LIKE 'sched-news-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs WHERE entity_type = 'Banner'
					AND entity_id IN (SELECT CAST(id AS TEXT) FROM banners WHERE title LIKE 'sched-banner-%')
				""");
		jdbcTemplate.update("DELETE FROM news WHERE slug LIKE 'sched-news-%'");
		jdbcTemplate.update("DELETE FROM banners WHERE title LIKE 'sched-banner-%'");
	}

	@Test
	void publishedNewsRespectsScheduleWindow() throws Exception {
		MockHttpSession admin = loginAdmin();
		createNews(admin, "sched-news-future", FUTURE, null);
		createNews(admin, "sched-news-expired", PAST_EARLIER, PAST);
		createNews(admin, "sched-news-live", PAST, FUTURE);

		MvcResult list = mockMvc.perform(get("/api/news")).andExpect(status().isOk()).andReturn();
		String body = list.getResponse().getContentAsString();
		assertThat(body).contains("sched-news-live");
		assertThat(body).doesNotContain("sched-news-future");
		assertThat(body).doesNotContain("sched-news-expired");

		mockMvc.perform(get("/api/news/{slug}", "sched-news-live")).andExpect(status().isOk());
		mockMvc.perform(get("/api/news/{slug}", "sched-news-future")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/news/{slug}", "sched-news-expired")).andExpect(status().isNotFound());
	}

	@Test
	void activeBannerRespectsScheduleWindow() throws Exception {
		MockHttpSession admin = loginAdmin();
		createBanner(admin, "sched-banner-future", FUTURE, null);
		createBanner(admin, "sched-banner-expired", PAST_EARLIER, PAST);
		createBanner(admin, "sched-banner-live", PAST, FUTURE);

		MvcResult result = mockMvc.perform(get("/api/banners").param("position", "HOME_HERO"))
				.andExpect(status().isOk())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("sched-banner-live");
		assertThat(body).doesNotContain("sched-banner-future");
		assertThat(body).doesNotContain("sched-banner-expired");
	}

	@Test
	void scheduleEndMustBeAfterStart() throws Exception {
		MockHttpSession admin = loginAdmin();
		mockMvc.perform(post("/api/admin/banners")
						.session(admin)
						.contentType("application/json")
						.content(bannerJson("sched-banner-bad", PAST, PAST_EARLIER)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_BANNER_SCHEDULE"));

		mockMvc.perform(post("/api/admin/news")
						.session(admin)
						.contentType("application/json")
						.content(newsJson("sched-news-bad", PAST, PAST_EARLIER)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_NEWS_SCHEDULE"));
	}

	private void createNews(MockHttpSession admin, String slug, String publishedAt, String unpublishAt) throws Exception {
		mockMvc.perform(post("/api/admin/news")
						.session(admin)
						.contentType("application/json")
						.content(newsJson(slug, publishedAt, unpublishAt)))
				.andExpect(status().isCreated());
	}

	private void createBanner(MockHttpSession admin, String title, String publishAt, String unpublishAt)
			throws Exception {
		mockMvc.perform(post("/api/admin/banners")
						.session(admin)
						.contentType("application/json")
						.content(bannerJson(title, publishAt, unpublishAt)))
				.andExpect(status().isCreated());
	}

	private static String newsJson(String slug, String publishedAt, String unpublishAt) {
		return """
				{"title":"%s","slug":"%s","content":"排程測試內容","status":"PUBLISHED",
				 "publishedAt":"%s","unpublishAt":%s}
				""".formatted(slug, slug, publishedAt, unpublishAt == null ? "null" : "\"" + unpublishAt + "\"");
	}

	private static String bannerJson(String title, String publishAt, String unpublishAt) {
		return """
				{"title":"%s","imageUrl":"https://example.com/b.png","position":"HOME_HERO","status":"ACTIVE",
				 "publishAt":"%s","unpublishAt":%s}
				""".formatted(title, publishAt, unpublishAt == null ? "null" : "\"" + unpublishAt + "\"");
	}

	private MockHttpSession loginAdmin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"admin@luckybox.local\",\"password\":\"ChangeMe123!\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
