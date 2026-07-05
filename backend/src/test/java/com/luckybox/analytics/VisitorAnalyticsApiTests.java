package com.luckybox.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class VisitorAnalyticsApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM visitor_sessions WHERE visitor_id LIKE 'visitor-test-%'");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'visitor-test-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'visitor-test-%'");
	}

	@Test
	void visitIsRecordedAndLinkedToRegistrationForDashboardConversion() throws Exception {
		String visitorId = "visitor-test-" + UUID.randomUUID();
		String email = visitorId + "@example.com";

		mockMvc.perform(post("/api/analytics/visit")
						.contentType("application/json")
						.content("""
								{"visitorId":"%s","path":"/"}
								""".formatted(visitorId)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.visitorId").value(visitorId))
				.andExpect(jsonPath("$.visitCount").value(1))
				.andExpect(jsonPath("$.registered").value(false));

		mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Visitor 測試玩家",
								  "visitorId": "%s"
								}
								""".formatted(email, visitorId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email));

		Integer linkedVisitors = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM visitor_sessions
				WHERE visitor_id = ? AND registered_user_id IS NOT NULL AND registered_at IS NOT NULL
				""", Integer.class, visitorId);
		assertThat(linkedVisitors).isEqualTo(1);

		mockMvc.perform(get("/api/admin/dashboard").session(loginAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productMetrics[0].key").value("visitorToRegistration"))
				.andExpect(jsonPath("$.productMetrics[0].value").value("100%"))
				.andExpect(jsonPath("$.productMetrics[0].helper").value("已註冊訪客 / 匿名訪客"));
	}

	@Test
	void invalidVisitorIdIsRejected() throws Exception {
		mockMvc.perform(post("/api/analytics/visit")
						.contentType("application/json")
						.content("""
								{"visitorId":"bad id","path":"/"}
								"""))
				.andExpect(status().isBadRequest());
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
}
