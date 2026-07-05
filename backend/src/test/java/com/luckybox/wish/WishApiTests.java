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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WishApiTests {

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
	void memberWishAppearsMaskedOnPublicWallAndAdminCanHideIt() throws Exception {
		Member member = registerMember();

		MvcResult createResult = mockMvc.perform(post("/api/account/wishes")
						.session(member.session())
						.contentType("application/json")
						.content("""
								{"content":"希望可以上架更多鬼滅之刃限定週邊抽賞"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.authorName").value("Wishing Fan"))
				.andReturn();
		long wishId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		// 公開牆：作者匿名遮罩、不外洩 email。
		MvcResult publicResult = mockMvc.perform(get("/api/wishes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) wishId))
				.andExpect(jsonPath("$[0].authorName").value("Wi**"))
				.andExpect(jsonPath("$[0].content").value("希望可以上架更多鬼滅之刃限定週邊抽賞"))
				.andReturn();
		assertThat(publicResult.getResponse().getContentAsString()).doesNotContain(member.email());

		// 會員可看到自己的願望（顯示真實暱稱）。
		mockMvc.perform(get("/api/account/wishes").session(member.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) wishId))
				.andExpect(jsonPath("$[0].authorName").value("Wishing Fan"))
				.andExpect(jsonPath("$[0].status").value("APPROVED"));

		// 管理員列表可看到 email，並可下架。
		MockHttpSession adminSession = loginAdmin();
		mockMvc.perform(get("/api/admin/wishes").session(adminSession).param("status", "APPROVED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].authorEmail").value(member.email()));

		mockMvc.perform(patch("/api/admin/wishes/{wishId}", wishId)
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"HIDDEN","note":"測試下架"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("HIDDEN"));

		// 下架後公開牆不再顯示。
		MvcResult afterHide = mockMvc.perform(get("/api/wishes"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(afterHide.getResponse().getContentAsString()).doesNotContain("希望可以上架更多鬼滅之刃限定週邊抽賞");
	}

	@Test
	void normalMemberCannotModerateWishes() throws Exception {
		Member member = registerMember();
		MvcResult createResult = mockMvc.perform(post("/api/account/wishes")
						.session(member.session())
						.contentType("application/json")
						.content("""
								{"content":"希望舉辦限定週邊抽賞"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		long wishId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(patch("/api/admin/wishes/{wishId}", wishId)
						.session(member.session())
						.contentType("application/json")
						.content("""
								{"status":"HIDDEN"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsTooShortWishContent() throws Exception {
		Member member = registerMember();
		mockMvc.perform(post("/api/account/wishes")
						.session(member.session())
						.contentType("application/json")
						.content("""
								{"content":"嗯"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_WISH_CONTENT"));
	}

	@Test
	void publicWishWallRequiresNoAuthentication() throws Exception {
		mockMvc.perform(get("/api/wishes"))
				.andExpect(status().isOk());
	}

	private Member registerMember() throws Exception {
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
		return new Member(email, (MockHttpSession) result.getRequest().getSession(false));
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

	private record Member(String email, MockHttpSession session) {
	}
}
