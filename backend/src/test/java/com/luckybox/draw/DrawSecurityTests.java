package com.luckybox.draw;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class DrawSecurityTests {

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
					WHERE c.slug LIKE 'sec-test-%'
				  )
				""");
		jdbcTemplate.update("DELETE FROM user_prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sec-test-%')");
		jdbcTemplate.update("DELETE FROM draw_results WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sec-test-%')");
		jdbcTemplate.update("DELETE FROM draw_orders WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sec-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sec-test-%')");
		jdbcTemplate.update("DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sec-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'sec-test-%'");
	}

	@Test
	void rejectsDrawWhenNotLoggedIn() throws Exception {
		mockMvc.perform(post("/api/account/draw-orders")
						.contentType("application/json")
						.content("""
								{"campaignSlug":"sec-test-anything","quantity":1,"idempotencyKey":"%s"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	void rejectsOutOfRangeQuantity() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCampaign(5);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":99,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.details.quantity").value("單次最多 10 抽"));

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":0,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.details.quantity").value("至少需要抽 1 次"));
	}

	@Test
	void pricingIsServerAuthoritative() throws Exception {
		// Client-submitted pricing fields are ignored; point spend is computed from the campaign's DB price.
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCampaign(5);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 3,
								  "idempotencyKey": "%s",
								  "pricePerDraw": 1,
								  "pointSpent": 1,
								  "originalPointSpent": 1,
								  "cashPointSpent": 1,
								  "bonusPointSpent": 1
								}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalPointSpent").value(300))
				.andExpect(jsonPath("$.pointSpent").value(300));
	}

	@Test
	void deductsBonusPointsBeforeCash() throws Exception {
		// collector grants 1000 cash + 150 bonus; drawing 200 must consume all 150 bonus then 50 cash.
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCampaign(5);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pointSpent").value(200));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.cashPointBalance").value(950));
	}

	@Test
	void idempotencyKeyReuseAcrossUsersReturnsConflictNotServerError() throws Exception {
		String slug = seedCampaign(5);
		String sharedKey = UUID.randomUUID().toString();

		MockHttpSession userA = registerUser();
		topUpCollector(userA);
		mockMvc.perform(post("/api/account/draw-orders")
						.session(userA)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(slug, sharedKey)))
				.andExpect(status().isCreated());

		MockHttpSession userB = registerUser();
		topUpCollector(userB);
		mockMvc.perform(post("/api/account/draw-orders")
						.session(userB)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(slug, sharedKey)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DRAW_IDEMPOTENCY_CONFLICT"));
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "sec-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "安全測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
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
		int orderId = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
	}

	private String seedCampaign(int tickets) {
		String slug = "sec-test-" + UUID.randomUUID().toString().substring(0, 8);
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
				""", slug, "安全測試池", "安全測試", "安全測試資料", tickets, tickets, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '安全測試賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", resolvedCampaignId, tickets, tickets, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		long resolvedPrizeId = prizeId == null ? 0 : prizeId;
		for (int index = 1; index <= tickets; index++) {
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
