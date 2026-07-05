package com.luckybox.compatibility;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
class CompatibilityApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteCompatibilityDrawCampaigns() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE reference_type = 'DrawOrder'
				  AND reference_id IN (
					SELECT d.id FROM draw_orders d
					JOIN kuji_campaigns c ON c.id = d.campaign_id
					WHERE c.slug LIKE 'compat-draw-%'
				  )
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'compat-draw-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'compat-draw-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM coupon_usages
				WHERE reference_type = 'DrawOrder'
				  AND reference_id IN (
					SELECT d.id FROM draw_orders d
					JOIN kuji_campaigns c ON c.id = d.campaign_id
					WHERE c.slug LIKE 'compat-draw-%'
				  )
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'compat-draw-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'compat-draw-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'compat-draw-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'compat-draw-%'");
	}

	@Test
	void legacyAccountWalletAndPaymentRoutesProxyCurrentEndpoints() throws Exception {
		MockHttpSession session = registerUser();

		mockMvc.perform(get("/api/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").exists());

		mockMvc.perform(get("/api/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.topUpPlans.length()").value(3));

		mockMvc.perform(get("/api/wallet/ledger").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		MvcResult createResult = mockMvc.perform(post("/api/payments/top-up")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"starter"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();

		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(post("/api/payments/mock/complete")
						.session(session)
						.contentType("application/json")
						.content("""
								{"orderId": %d}
								""".formatted(orderId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		mockMvc.perform(get("/api/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(100));
	}

	@Test
	void legacyAddressRoutesProxyCurrentAccountAddressEndpoints() throws Exception {
		MockHttpSession session = registerUser();

		MvcResult createResult = mockMvc.perform(post("/api/addresses")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "recipientName": "相容地址",
								  "phone": "0912345678",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "中正區",
								  "addressLine": "測試路 1 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.defaultAddress").value(true))
				.andReturn();

		int addressId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(patch("/api/addresses/{addressId}", addressId)
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "recipientName": "相容地址",
								  "phone": "0987654321",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "大安區",
								  "addressLine": "更新路 2 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.district").value("大安區"));

		mockMvc.perform(get("/api/addresses").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(delete("/api/addresses/{addressId}", addressId).session(session))
				.andExpect(status().isNoContent());
	}

	@Test
	void legacyDrawDetailRoutesProxyCurrentMemberDrawEndpoints() throws Exception {
		MockHttpSession session = registerUser();
		topUp(session, "starter");
		String slug = seedCampaign(2);

		MvcResult drawResult = mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.campaignSlug").value(slug))
				.andExpect(jsonPath("$.results.length()").value(1))
				.andReturn();

		String body = drawResult.getResponse().getContentAsString();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).intValue();
		int resultId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.results[0].id")).intValue();

		mockMvc.perform(get("/api/account/draw-orders/{orderId}", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.results[0].id").value(resultId));

		mockMvc.perform(get("/api/draw-orders/{orderId}", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.results[0].id").value(resultId));

		mockMvc.perform(get("/api/draw-results/{resultId}", resultId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(resultId))
				.andExpect(jsonPath("$.prizeName").value("相容測試賞"));

		MockHttpSession otherSession = registerUser();
		mockMvc.perform(get("/api/draw-orders/{orderId}", orderId).session(otherSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DRAW_ORDER_NOT_FOUND"));

		mockMvc.perform(get("/api/draw-results/{resultId}", resultId).session(otherSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DRAW_RESULT_NOT_FOUND"));
	}

	@Test
	void campaignProbabilityAliasReturnsCurrentPrizeProbabilities() throws Exception {
		mockMvc.perform(get("/api/campaigns/star-collection-vol-1/probabilities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("star-collection-vol-1"))
				.andExpect(jsonPath("$.remainingTickets").value(80))
				.andExpect(jsonPath("$.prizes.length()").value(6))
				.andExpect(jsonPath("$.prizes[0].probability").value(1.25));
	}

	private void topUp(MockHttpSession session, String planId) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/payments/top-up")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"%s"}
								""".formatted(planId)))
				.andExpect(status().isCreated())
				.andReturn();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/payments/mock/complete")
						.session(session)
						.contentType("application/json")
						.content("""
								{"orderId": %d}
								""".formatted(orderId)))
				.andExpect(status().isOk());
	}

	private String seedCampaign(int ticketCount) {
		String slug = "compat-draw-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""",
				slug,
				"相容測試池",
				"相容測試",
				"相容 API 抽賞測試資料",
				ticketCount,
				ticketCount,
				now,
				"test-seed-" + slug,
				now,
				now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long prizeId = insertPrize(campaignId == null ? 0 : campaignId, ticketCount, now);
		for (int index = 1; index <= ticketCount; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", campaignId, prizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		return slug;
	}

	private long insertPrize(long campaignId, int ticketCount, String now) {
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '相容測試賞', '相容 API 測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", campaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "compat-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "相容測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
