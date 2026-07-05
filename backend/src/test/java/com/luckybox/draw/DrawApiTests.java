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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DrawApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteDrawTestCampaigns() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE reference_type = 'DrawOrder'
				  AND reference_id IN (
					SELECT d.id FROM draw_orders d
					JOIN kuji_campaigns c ON c.id = d.campaign_id
					WHERE c.slug LIKE 'draw-test-%'
				  )
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'draw-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'draw-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM coupon_usages
				WHERE coupon_id IN (SELECT id FROM coupons WHERE code LIKE 'DRAWCOUPON%')
					OR (
						reference_type = 'DrawOrder'
						AND reference_id IN (
							SELECT d.id FROM draw_orders d
							JOIN kuji_campaigns c ON c.id = d.campaign_id
							WHERE c.slug LIKE 'draw-test-%'
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'draw-test-%')
				""");
		jdbcTemplate.update("DELETE FROM coupons WHERE code LIKE 'DRAWCOUPON%'");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'draw-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'draw-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'draw-test-%'");
	}

	@Test
	void drawsMultipleTicketsAndKeepsRetryIdempotent() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCampaign(5);
		String idempotencyKey = UUID.randomUUID().toString();

		MvcResult drawResult = mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, idempotencyKey)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.campaignSlug").value(slug))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.pointSpent").value(200))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.balanceAfter").value(950))
				.andExpect(jsonPath("$.results.length()").value(2))
				.andReturn();

		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(drawResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, idempotencyKey)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.results.length()").value(2));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cashPointBalance").value(950))
				.andExpect(jsonPath("$.bonusPointBalance").value(0));

		mockMvc.perform(get("/api/campaigns/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remainingTickets").value(3));

		assertCount("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = campaign_id(?) AND status = 'DRAWN'", slug, 2);
		assertCount("SELECT COUNT(*) FROM user_prizes up JOIN kuji_campaigns c ON c.id = up.campaign_id WHERE c.slug = ?", slug, 2);
	}

	@Test
	void appliesDiscountCouponAndRecordsUsage() throws Exception {
		MockHttpSession session = registerUser();
		topUpStarter(session);
		String slug = seedCampaign(3);
		String couponCode = seedDiscountCoupon(150, 200, 2);
		String idempotencyKey = UUID.randomUUID().toString();

		MvcResult drawResult = mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 2,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(slug, idempotencyKey, couponCode.toLowerCase())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.campaignSlug").value(slug))
				.andExpect(jsonPath("$.originalPointSpent").value(200))
				.andExpect(jsonPath("$.discountAmount").value(150))
				.andExpect(jsonPath("$.pointSpent").value(50))
				.andExpect(jsonPath("$.couponCode").value(couponCode))
				.andExpect(jsonPath("$.balanceAfter").value(50))
				.andExpect(jsonPath("$.results.length()").value(2))
				.andReturn();

		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(drawResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 2,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(slug, idempotencyKey, couponCode)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.discountAmount").value(150));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cashPointBalance").value(50));

		Integer usedCount = jdbcTemplate.queryForObject(
				"SELECT used_count FROM coupons WHERE code = ?", Integer.class, couponCode);
		assertThat(usedCount).isEqualTo(1);
		Integer usageCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM coupon_usages cu
				JOIN coupons c ON c.id = cu.coupon_id
				WHERE c.code = ? AND cu.reference_type = 'DrawOrder' AND cu.reference_id = ?
				""", Integer.class, couponCode, orderId);
		assertThat(usageCount).isEqualTo(1);
	}

	@Test
	void rejectsDiscountCouponWhenMinSpendIsNotReached() throws Exception {
		MockHttpSession session = registerUser();
		topUpStarter(session);
		String slug = seedCampaign(2);
		String couponCode = seedDiscountCoupon(50, 300, 5);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 1,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(slug, UUID.randomUUID(), couponCode)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COUPON_MIN_SPEND_NOT_REACHED"));

		Integer usedCount = jdbcTemplate.queryForObject(
				"SELECT used_count FROM coupons WHERE code = ?", Integer.class, couponCode);
		assertThat(usedCount).isZero();
		mockMvc.perform(get("/api/campaigns/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remainingTickets").value(2));
	}

	@Test
	void rejectsVipDiscountCouponUntilMemberQualifies() throws Exception {
		RegisteredUser user = registerRegisteredUser();
		topUpCollector(user.session());
		String slug = seedCampaign(3);
		String couponCode = seedDiscountCoupon(25, 100, 5, "SILVER");

		mockMvc.perform(post("/api/account/draw-orders")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 1,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(slug, UUID.randomUUID(), couponCode)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("COUPON_VIP_REQUIRED"));

		seedCompletedDrawSpend(user.userId(), campaignId(slug), 1200);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 1,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(slug, UUID.randomUUID(), couponCode)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalPointSpent").value(100))
				.andExpect(jsonPath("$.discountAmount").value(25))
				.andExpect(jsonPath("$.pointSpent").value(75));
	}

	@Test
	void rejectsDiscountCouponAlreadyUsedBySameUser() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String firstSlug = seedCampaign(2);
		String secondSlug = seedCampaign(2);
		String couponCode = seedDiscountCoupon(10, 100, 10);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 1,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(firstSlug, UUID.randomUUID(), couponCode)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.discountAmount").value(10));

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "campaignSlug": "%s",
								  "quantity": 1,
								  "idempotencyKey": "%s",
								  "couponCode": "%s"
								}
								""".formatted(secondSlug, UUID.randomUUID(), couponCode)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"));

		Integer usageCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM coupon_usages cu
				JOIN coupons c ON c.id = cu.coupon_id
				WHERE c.code = ?
				""", Integer.class, couponCode);
		assertThat(usageCount).isEqualTo(1);
	}

	@Test
	void rejectsInsufficientBalance() throws Exception {
		MockHttpSession session = registerUser();
		String slug = seedCampaign(3);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
	}

	@Test
	void rejectsInsufficientRemainingTickets() throws Exception {
		MockHttpSession session = registerUser();
		topUpStarter(session);
		String slug = seedCampaign(1);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DRAW_REMAINING_NOT_ENOUGH"));
	}

	@Test
	void awardsLastPrizeWhenCampaignSellsOut() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCampaignWithLastPrize(2);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.results.length()").value(3))
				.andExpect(jsonPath("$.results[0].lastPrize").value(false))
				.andExpect(jsonPath("$.results[1].lastPrize").value(false))
				.andExpect(jsonPath("$.results[2].lastPrize").value(true));

		mockMvc.perform(get("/api/campaigns/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remainingTickets").value(0));

		String status = jdbcTemplate.queryForObject("SELECT status FROM kuji_campaigns WHERE slug = ?", String.class, slug);
		org.assertj.core.api.Assertions.assertThat(status).isEqualTo("SOLD_OUT");
		assertCount("SELECT COUNT(*) FROM user_prizes up JOIN kuji_campaigns c ON c.id = up.campaign_id WHERE c.slug = ?", slug, 3);
		assertCount("""
				SELECT COUNT(*) FROM draw_results r
				JOIN prizes p ON p.id = r.prize_id
				JOIN kuji_campaigns c ON c.id = r.campaign_id
				WHERE c.slug = ? AND p.is_last_prize = 1
				""", slug, 1);
		Integer lastPrizeRemaining = jdbcTemplate.queryForObject("""
				SELECT remaining_quantity FROM prizes p
				JOIN kuji_campaigns c ON c.id = p.campaign_id
				WHERE c.slug = ? AND p.is_last_prize = 1
				""", Integer.class, slug);
		org.assertj.core.api.Assertions.assertThat(lastPrizeRemaining).isZero();
	}

	@Test
	void doesNotAwardLastPrizeBeforeSellOut() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCampaignWithLastPrize(3);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.results.length()").value(2))
				.andExpect(jsonPath("$.results[0].lastPrize").value(false))
				.andExpect(jsonPath("$.results[1].lastPrize").value(false));

		Integer remaining = jdbcTemplate.queryForObject("SELECT remaining_tickets FROM kuji_campaigns WHERE slug = ?", Integer.class, slug);
		org.assertj.core.api.Assertions.assertThat(remaining).isEqualTo(1);
		String status = jdbcTemplate.queryForObject("SELECT status FROM kuji_campaigns WHERE slug = ?", String.class, slug);
		org.assertj.core.api.Assertions.assertThat(status).isEqualTo("LIVE");
		Integer lastPrizeRemaining = jdbcTemplate.queryForObject("""
				SELECT remaining_quantity FROM prizes p
				JOIN kuji_campaigns c ON c.id = p.campaign_id
				WHERE c.slug = ? AND p.is_last_prize = 1
				""", Integer.class, slug);
		org.assertj.core.api.Assertions.assertThat(lastPrizeRemaining).isEqualTo(1);
	}

	private MockHttpSession registerUser() throws Exception {
		return registerRegisteredUser().session();
	}

	private RegisteredUser registerRegisteredUser() throws Exception {
		String email = "draw-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "抽賞測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new RegisteredUser(userId, (MockHttpSession) result.getRequest().getSession(false));
	}

	private void topUpCollector(MockHttpSession session) throws Exception {
		topUp(session, "collector");
	}

	private void topUpStarter(MockHttpSession session) throws Exception {
		topUp(session, "starter");
	}

	private void topUp(MockHttpSession session, String planId) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"%s"}
								""".formatted(planId)))
				.andExpect(status().isCreated())
				.andReturn();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
	}

	private String seedCampaign(int ticketCount) {
		String slug = "draw-test-" + UUID.randomUUID().toString().substring(0, 8);
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
				"抽賞測試池",
				"抽賞測試",
				"抽賞 API 測試資料",
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
				VALUES (?, 'A', '測試賞', '抽賞測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", campaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private String seedDiscountCoupon(int value, int minSpend, Integer usageLimit) {
		return seedDiscountCoupon(value, minSpend, usageLimit, null);
	}

	private String seedDiscountCoupon(int value, int minSpend, Integer usageLimit, String vipTier) {
		String code = "DRAWCOUPON" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String now = Instant.now().toString();
		String startsAt = Instant.now().minusSeconds(60).toString();
		String endsAt = Instant.now().plusSeconds(3600).toString();
		jdbcTemplate.update("""
				INSERT INTO coupons (
					code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at
				)
				VALUES (?, 'DISCOUNT', ?, ?, ?, ?, 0, ?, ?, 'ACTIVE', ?, ?)
				""", code, vipTier, value, minSpend, usageLimit, startsAt, endsAt, now, now);
		return code;
	}

	private long campaignId(String slug) {
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		return campaignId == null ? 0 : campaignId;
	}

	private void seedCompletedDrawSpend(long userId, long campaignId, int pointSpent) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, status, idempotency_key, created_at, completed_at
				)
				VALUES (?, ?, 1, ?, 'COMPLETED', ?, ?, ?)
				""", userId, campaignId, pointSpent, UUID.randomUUID().toString(), now, now);
	}

	private String seedCampaignWithLastPrize(int regularTickets) {
		String slug = "draw-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 1, ?, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""",
				slug,
				"最後賞測試池",
				"最後賞測試",
				"最後賞 API 測試資料",
				regularTickets,
				regularTickets,
				now,
				"最後一張普通籤被抽出時觸發最後賞。",
				"test-seed-" + slug,
				now,
				now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		long regularPrizeId = insertPrize(resolvedCampaignId, regularTickets, now);
		for (int index = 1; index <= regularTickets; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", resolvedCampaignId, regularPrizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'LAST', '最後賞', '售完時觸發的測試獎項', NULL, 1, 1, 99, 1, ?, ?)
				""", resolvedCampaignId, now, now);
		return slug;
	}

	private void assertCount(String sql, String slug, int expected) {
		String resolvedSql = sql.replace("campaign_id(?)", "(SELECT id FROM kuji_campaigns WHERE slug = ?)");
		Integer count = jdbcTemplate.queryForObject(resolvedSql, Integer.class, slug);
		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
	}

	private record RegisteredUser(long userId, MockHttpSession session) {
	}
}
