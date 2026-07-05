package com.luckybox.wish;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "luckybox.wish.auto-approve=false")
class WishModerationApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteWishTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'Wish'
					AND entity_id IN (
						SELECT CAST(id AS TEXT) FROM wishes
						WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'wish-user-%')
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wishes
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'wish-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'wish-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'wish-user-%'");
	}

	@Test
	void pendingWishIsHiddenUntilAdminApproves() throws Exception {
		MockHttpSession member = registerMember();

		MvcResult createResult = mockMvc.perform(post("/api/account/wishes")
						.session(member)
						.contentType("application/json")
						.content("""
								{"content":"希望可以開出更多隱藏款"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();
		long wishId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		// 審核前公開牆看不到。
		MvcResult beforeApprove = mockMvc.perform(get("/api/wishes"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(beforeApprove.getResponse().getContentAsString()).doesNotContain("希望可以開出更多隱藏款");

		// 管理員核准。
		MockHttpSession adminSession = loginAdmin();
		mockMvc.perform(patch("/api/admin/wishes/{wishId}", wishId)
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"APPROVED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"));

		// 核准後公開牆顯示（匿名）。
		mockMvc.perform(get("/api/wishes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) wishId))
				.andExpect(jsonPath("$[0].authorName").value("Wi**"))
				.andExpect(jsonPath("$[0].content").value("希望可以開出更多隱藏款"));
	}

	private MockHttpSession registerMember() throws Exception {
		String email = "wish-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Wishing Fan"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
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
