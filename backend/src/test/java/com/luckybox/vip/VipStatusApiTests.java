package com.luckybox.vip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
@TestPropertySource(properties = {
		"luckybox.vip.silver-threshold=200",
		"luckybox.vip.gold-threshold=500",
		"luckybox.vip.platinum-threshold=1000"
})
class VipStatusApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM draw_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'vip-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'vip-test-%'");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'vip-test-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'vip-test-%'");
	}

	@Test
	void vipTierUpgradesWithCumulativeDrawSpend() throws Exception {
		String email = "vip-test-" + UUID.randomUUID() + "@example.com";
		Registered user = registerUser(email);
		long campaignId = seedCampaign();

		// 尚無消費 → REGULAR，下一級 SILVER(200)。
		mockMvc.perform(get("/api/account/vip").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tier").value("REGULAR"))
				.andExpect(jsonPath("$.tierLabel").value("一般會員"))
				.andExpect(jsonPath("$.totalSpend").value(0))
				.andExpect(jsonPath("$.nextTier").value("SILVER"))
				.andExpect(jsonPath("$.nextTierThreshold").value(200))
				.andExpect(jsonPath("$.spendToNextTier").value(200))
				.andExpect(jsonPath("$.progressPercent").value(0));

		// 消費 250 → SILVER，進度 (250-200)/(500-200)=17%。
		seedCompletedDrawOrder(user.userId(), campaignId, 250);
		mockMvc.perform(get("/api/account/vip").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tier").value("SILVER"))
				.andExpect(jsonPath("$.totalSpend").value(250))
				.andExpect(jsonPath("$.nextTier").value("GOLD"))
				.andExpect(jsonPath("$.spendToNextTier").value(250))
				.andExpect(jsonPath("$.progressPercent").value(17));
		assertThat(vipLevel(user.userId())).isEqualTo("SILVER");

		// 再消費 800（累積 1050）→ PLATINUM，已達頂級。
		seedCompletedDrawOrder(user.userId(), campaignId, 800);
		mockMvc.perform(get("/api/account/vip").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tier").value("PLATINUM"))
				.andExpect(jsonPath("$.totalSpend").value(1050))
				.andExpect(jsonPath("$.nextTier").doesNotExist())
				.andExpect(jsonPath("$.spendToNextTier").value(0))
				.andExpect(jsonPath("$.progressPercent").value(100));
		assertThat(vipLevel(user.userId())).isEqualTo("PLATINUM");
	}

	@Test
	void vipStatusRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/account/vip"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	private Registered registerUser(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"displayName\":\"VIP測試\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new Registered(userId, (MockHttpSession) result.getRequest().getSession(false));
	}

	private long seedCampaign() {
		String slug = "vip-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, 'VIP 測試池', 'VIP', 'VIP 測試資料', NULL, NULL, 'MIXED', NULL, 'LuckyBox Test',
					100, 100, 100, 'LIVE', ?, NULL, '測試', '測試', 0, NULL, 'SERVER_RANDOM', NULL, NULL, ?, ?)
				""", slug, now, now, now);
		Long id = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		return id == null ? 0 : id;
	}

	private void seedCompletedDrawOrder(long userId, long campaignId, int pointSpent) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, status, idempotency_key, created_at, completed_at
				)
				VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, ?)
				""", userId, campaignId, Math.max(1, pointSpent / 100), pointSpent, UUID.randomUUID().toString(), now, now);
	}

	private String vipLevel(long userId) {
		return jdbcTemplate.queryForObject("SELECT vip_level FROM users WHERE id = ?", String.class, userId);
	}

	private record Registered(long userId, MockHttpSession session) {
	}
}
