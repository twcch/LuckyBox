package com.luckybox.coupon;

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
class AccountCouponApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAccountCouponTestData() {
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'account-coupon-user-%')
					OR campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'account-coupon-vip-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'account-coupon-user-%')
					OR (
						reference_type = 'Coupon'
						AND reference_id IN (SELECT id FROM coupons WHERE code LIKE 'ACCOUNTCOUPON%')
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM coupon_usages
				WHERE coupon_id IN (SELECT id FROM coupons WHERE code LIKE 'ACCOUNTCOUPON%')
					OR user_id IN (SELECT id FROM users WHERE email LIKE 'account-coupon-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'account-coupon-user-%')
					OR entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'account-coupon-user-%'
					)
				""");
		jdbcTemplate.update("DELETE FROM coupons WHERE code LIKE 'ACCOUNTCOUPON%'");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'account-coupon-user-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'account-coupon-vip-%'");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'account-coupon-user-%'");
	}

	@Test
	void redeemsPointBonusCouponIntoWalletOnceAndHidesUsedCoupon() throws Exception {
		MockHttpSession session = registerUser();
		SeededCoupon coupon = seedCoupon("POINT_BONUS", 80, 0, 5, "ACTIVE");

		MvcResult availableBefore = mockMvc.perform(get("/api/account/coupons").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(availableBefore.getResponse().getContentAsString()).contains(coupon.code());

		mockMvc.perform(post("/api/account/coupons/{couponId}/redeem", coupon.id()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.couponId").value((int) coupon.id()))
				.andExpect(jsonPath("$.code").value(coupon.code()))
				.andExpect(jsonPath("$.pointAmount").value(80))
				.andExpect(jsonPath("$.bonusPointBalance").value(80))
				.andExpect(jsonPath("$.totalAvailableBalance").value(80));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(80))
				.andExpect(jsonPath("$.wallet.totalAvailableBalance").value(80))
				.andExpect(jsonPath("$.ledger[0].type").value("COUPON_BONUS"))
				.andExpect(jsonPath("$.ledger[0].amount").value(80))
				.andExpect(jsonPath("$.ledger[0].pointKind").value("BONUS"))
				.andExpect(jsonPath("$.ledger[0].referenceType").value("Coupon"))
				.andExpect(jsonPath("$.ledger[0].referenceId").value((int) coupon.id()));

		MvcResult availableAfter = mockMvc.perform(get("/api/account/coupons").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(availableAfter.getResponse().getContentAsString()).doesNotContain(coupon.code());

		mockMvc.perform(post("/api/account/coupons/{couponId}/redeem", coupon.id()).session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"));

		Integer usedCount = jdbcTemplate.queryForObject(
				"SELECT used_count FROM coupons WHERE id = ?", Integer.class, coupon.id());
		assertThat(usedCount).isEqualTo(1);
		Integer usageCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM coupon_usages
				WHERE coupon_id = ? AND point_amount = 80 AND discount_amount = 0 AND reference_type = 'Coupon'
				""", Integer.class, coupon.id());
		assertThat(usageCount).isEqualTo(1);
	}

	@Test
	void rejectsDirectRedemptionForDiscountCoupon() throws Exception {
		MockHttpSession session = registerUser();
		SeededCoupon coupon = seedCoupon("DISCOUNT", 50, 100, 5, "ACTIVE");

		mockMvc.perform(post("/api/account/coupons/{couponId}/redeem", coupon.id()).session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COUPON_NOT_REDEEMABLE"));

		Integer usedCount = jdbcTemplate.queryForObject(
				"SELECT used_count FROM coupons WHERE id = ?", Integer.class, coupon.id());
		assertThat(usedCount).isZero();
	}

	@Test
	void rejectsArchivedPointBonusCoupon() throws Exception {
		MockHttpSession session = registerUser();
		SeededCoupon coupon = seedCoupon("POINT_BONUS", 30, 0, 5, "ARCHIVED");

		mockMvc.perform(post("/api/account/coupons/{couponId}/redeem", coupon.id()).session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COUPON_NOT_ACTIVE"));
	}

	@Test
	void vipRestrictedPointBonusCouponRequiresQualifiedTier() throws Exception {
		RegisteredUser user = registerRegisteredUser();
		SeededCoupon coupon = seedCoupon("POINT_BONUS", 100, 0, 5, "ACTIVE", "SILVER");

		MvcResult regularList = mockMvc.perform(get("/api/account/coupons").session(user.session()))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(regularList.getResponse().getContentAsString()).doesNotContain(coupon.code());

		mockMvc.perform(post("/api/account/coupons/{couponId}/redeem", coupon.id()).session(user.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("COUPON_VIP_REQUIRED"));

		long campaignId = seedVipCampaign();
		seedCompletedDrawOrder(user.userId(), campaignId, 1200);

		MvcResult vipList = mockMvc.perform(get("/api/account/coupons").session(user.session()))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(vipList.getResponse().getContentAsString())
				.contains(coupon.code())
				.contains("\"vipTier\":\"SILVER\"")
				.contains("\"vipTierLabel\":\"銀卡以上\"");

		mockMvc.perform(post("/api/account/coupons/{couponId}/redeem", coupon.id()).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.couponId").value((int) coupon.id()))
				.andExpect(jsonPath("$.pointAmount").value(100));
	}

	private MockHttpSession registerUser() throws Exception {
		return registerRegisteredUser().session();
	}

	private RegisteredUser registerRegisteredUser() throws Exception {
		String email = "account-coupon-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "優惠券會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new RegisteredUser(userId, (MockHttpSession) result.getRequest().getSession(false));
	}

	private SeededCoupon seedCoupon(String type, int value, int minSpend, int usageLimit, String status) {
		return seedCoupon(type, value, minSpend, usageLimit, status, null);
	}

	private SeededCoupon seedCoupon(
			String type,
			int value,
			int minSpend,
			int usageLimit,
			String status,
			String vipTier) {
		String code = "ACCOUNTCOUPON" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String now = Instant.now().toString();
		String startsAt = Instant.now().minusSeconds(60).toString();
		String endsAt = Instant.now().plusSeconds(3600).toString();
		jdbcTemplate.update("""
				INSERT INTO coupons (
					code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
				""", code, type, vipTier, value, minSpend, usageLimit, startsAt, endsAt, status, now, now);
		Long couponId = jdbcTemplate.queryForObject("SELECT id FROM coupons WHERE code = ?", Long.class, code);
		return new SeededCoupon(couponId == null ? 0 : couponId, code);
	}

	private long seedVipCampaign() {
		String slug = "account-coupon-vip-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, 'VIP 優惠券測試池', 'VIP', 'VIP 優惠券測試資料', NULL, NULL, 'MIXED', NULL, 'LuckyBox Test',
					100, 100, 100, 'LIVE', ?, NULL, '測試', '測試', 0, NULL, 'SERVER_RANDOM', NULL, NULL, ?, ?)
				""", slug, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		return campaignId == null ? 0 : campaignId;
	}

	private void seedCompletedDrawOrder(long userId, long campaignId, int pointSpent) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, status, idempotency_key, created_at, completed_at
				)
				VALUES (?, ?, 1, ?, 'COMPLETED', ?, ?, ?)
				""", userId, campaignId, pointSpent, UUID.randomUUID().toString(), now, now);
	}

	private record RegisteredUser(long userId, MockHttpSession session) {
	}

	private record SeededCoupon(long id, String code) {
	}
}
