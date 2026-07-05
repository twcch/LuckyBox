package com.luckybox.draw;

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
		"luckybox.promo.spend-threshold=200",
		"luckybox.promo.spend-threshold-bonus=50"
})
class SpendThresholdPromoTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE reference_type = 'DrawOrder'
				  AND reference_id IN (
					SELECT d.id FROM draw_orders d
					JOIN kuji_campaigns c ON c.id = d.campaign_id
					WHERE c.slug LIKE 'spend-test-%'
				  )
				""");
		jdbcTemplate.update("DELETE FROM user_prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'spend-test-%')");
		jdbcTemplate.update("DELETE FROM draw_results WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'spend-test-%')");
		jdbcTemplate.update("DELETE FROM draw_orders WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'spend-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'spend-test-%')");
		jdbcTemplate.update("DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'spend-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'spend-test-%'");
	}

	@Test
	void grantsBonusOnceWhenCumulativeSpendCrossesThreshold() throws Exception {
		String email = "spend-" + UUID.randomUUID() + "@example.com";
		MockHttpSession session = registerUser(email);
		long userId = userIdByEmail(email);
		topUpCollector(session);
		String slug = seedCampaign(5);

		// Spend 200 in one order → crosses the 200 threshold → +50 bonus.
		draw(session, slug, 2);
		assertThresholdBonusCount(userId, 1);
		Integer amount = jdbcTemplate.queryForObject(
				"SELECT amount FROM wallet_ledger WHERE user_id = ? AND type = 'SPEND_THRESHOLD_BONUS'",
				Integer.class, userId);
		assertThat(amount).isEqualTo(50);

		// Spend 100 more (cumulative 300) → no additional grant.
		draw(session, slug, 1);
		assertThresholdBonusCount(userId, 1);
	}

	@Test
	void doesNotGrantBeforeThresholdReached() throws Exception {
		String email = "spend-" + UUID.randomUUID() + "@example.com";
		MockHttpSession session = registerUser(email);
		long userId = userIdByEmail(email);
		topUpCollector(session);
		String slug = seedCampaign(5);

		// Spend 100 (< 200) → no bonus yet.
		draw(session, slug, 1);
		assertThresholdBonusCount(userId, 0);
	}

	@Test
	void walletOverviewExposesSpendThresholdProgress() throws Exception {
		String email = "spend-" + UUID.randomUUID() + "@example.com";
		MockHttpSession session = registerUser(email);
		topUpCollector(session);
		String slug = seedCampaign(5);

		// 尚未消費：促銷啟用、未達標、剩餘等於門檻。
		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spendThresholdPromo.active").value(true))
				.andExpect(jsonPath("$.spendThresholdPromo.threshold").value(200))
				.andExpect(jsonPath("$.spendThresholdPromo.bonusPoints").value(50))
				.andExpect(jsonPath("$.spendThresholdPromo.totalSpend").value(0))
				.andExpect(jsonPath("$.spendThresholdPromo.remaining").value(200))
				.andExpect(jsonPath("$.spendThresholdPromo.reached").value(false));

		// 消費 200（抽 2 次 × 100）跨越門檻後：已達標、剩餘 0。
		draw(session, slug, 2);
		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spendThresholdPromo.totalSpend").value(200))
				.andExpect(jsonPath("$.spendThresholdPromo.remaining").value(0))
				.andExpect(jsonPath("$.spendThresholdPromo.reached").value(true));
	}

	private void assertThresholdBonusCount(long userId, int expected) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ? AND type = 'SPEND_THRESHOLD_BONUS'",
				Integer.class, userId);
		assertThat(count).isEqualTo(expected);
	}

	private void draw(MockHttpSession session, String slug, int quantity) throws Exception {
		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":%d,"idempotencyKey":"%s"}
								""".formatted(slug, quantity, UUID.randomUUID())))
				.andExpect(status().isCreated());
	}

	private MockHttpSession registerUser(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "消費門檻測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long userIdByEmail(String email) {
		Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", Long.class, email);
		return id == null ? 0 : id;
	}

	private void topUpCollector(MockHttpSession session) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"collector"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
	}

	private String seedCampaign(int ticketCount) {
		String slug = "spend-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 0, NULL, 'SERVER_RANDOM', NULL, NULL, ?, ?)
				""", slug, "消費門檻測試池", "消費門檻測試", "消費門檻測試資料", ticketCount, ticketCount, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '消費門檻測試賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", resolvedCampaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		long resolvedPrizeId = prizeId == null ? 0 : prizeId;
		for (int index = 1; index <= ticketCount; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", resolvedCampaignId, resolvedPrizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		return slug;
	}
}
