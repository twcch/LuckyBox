package com.luckybox.prizebox;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

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
class PrizeBoxApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deletePrizeBoxTestData() {
		jdbcTemplate.update("""
				DELETE FROM coupon_usages
				WHERE coupon_id IN (SELECT id FROM coupons WHERE code LIKE 'PRIZEBOXCOUPON%')
					OR (
						reference_type = 'Shipment'
						AND reference_id IN (
							SELECT s.id FROM shipments s
							JOIN users u ON u.id = s.user_id
							WHERE u.email LIKE 'prizebox-%'
						)
					)
				""");
		jdbcTemplate.update("DELETE FROM coupons WHERE code LIKE 'PRIZEBOXCOUPON%'");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'prizebox-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'prizebox-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'prizebox-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'prizebox-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'prizebox-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'prizebox-test-%'");
	}

	@Test
	void listsFiltersAndRequestsShipmentForPrizeBoxItems() throws Exception {
		UserSession user = registerUser();
		String slug = seedPrizeBoxItems(user.userId(), 2);
		long addressId = createAddress(user.session());

		MvcResult listResult = mockMvc.perform(get("/api/account/prizes").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.campaigns[0].slug").value(slug))
				.andExpect(jsonPath("$.statusCounts.IN_BOX").value(2))
				.andReturn();

		int firstPrizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(listResult.getResponse().getContentAsString(), "$.items[0].id")).intValue();
		int secondPrizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(listResult.getResponse().getContentAsString(), "$.items[1].id")).intValue();

		mockMvc.perform(get("/api/account/prizes")
						.session(user.session())
						.param("status", "IN_BOX")
						.param("campaignSlug", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.campaignSlug").value(slug));

		MvcResult shipmentResult = mockMvc.perform(post("/api/account/shipments")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d,%d]}
								""".formatted(addressId, firstPrizeId, secondPrizeId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andExpect(jsonPath("$.itemCount").value(2))
				.andExpect(jsonPath("$.shippingFee").value(80))
				.andExpect(jsonPath("$.recipientName").value("林小盒"))
				.andExpect(jsonPath("$.city").value("台北市"))
				.andExpect(jsonPath("$.items[0].status").value("SHIPMENT_REQUESTED"))
				.andReturn();
		int shipmentId = ((Number) com.jayway.jsonpath.JsonPath
				.read(shipmentResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(get("/api/account/prizes")
						.session(user.session())
						.param("status", "SHIPMENT_REQUESTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2));

		mockMvc.perform(get("/api/account/shipments").session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value("REQUESTED"))
				.andExpect(jsonPath("$[0].itemCount").value(2))
				.andExpect(jsonPath("$[0].items.length()").value(2));

		mockMvc.perform(get("/api/account/shipments/{shipmentId}", shipmentId).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(shipmentId))
				.andExpect(jsonPath("$.phone").value("0912345678"))
				.andExpect(jsonPath("$.postalCode").value("100"))
				.andExpect(jsonPath("$.addressLine").value("測試路 1 號"))
				.andExpect(jsonPath("$.carrier").doesNotExist())
				.andExpect(jsonPath("$.trackingNumber").doesNotExist())
				.andExpect(jsonPath("$.items.length()").value(2));

		mockMvc.perform(get("/api/account/shipments/{shipmentId}", shipmentId + 1000).session(user.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SHIPMENT_NOT_FOUND"));

		mockMvc.perform(post("/api/account/shipments")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d]}
								""".formatted(addressId, firstPrizeId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PRIZE_NOT_SHIPPABLE"));
	}

	@Test
	void appliesFreeShippingCouponWhenRequestingShipment() throws Exception {
		UserSession user = registerUser();
		seedPrizeBoxItems(user.userId(), 1);
		long addressId = createAddress(user.session());
		long couponId = seedCoupon("FREE_SHIPPING", 0, 5, "ACTIVE");

		MvcResult listResult = mockMvc.perform(get("/api/account/prizes").session(user.session()))
				.andExpect(status().isOk())
				.andReturn();
		int prizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(listResult.getResponse().getContentAsString(), "$.items[0].id")).intValue();

		mockMvc.perform(post("/api/account/shipments")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d],"couponId":%d}
								""".formatted(addressId, prizeId, couponId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andExpect(jsonPath("$.itemCount").value(1))
				.andExpect(jsonPath("$.shippingFee").value(0));

		Integer usedCount = jdbcTemplate.queryForObject(
				"SELECT used_count FROM coupons WHERE id = ?", Integer.class, couponId);
		assertThat(usedCount).isEqualTo(1);
		Integer usageCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM coupon_usages
				WHERE coupon_id = ?
					AND reference_type = 'Shipment'
					AND discount_amount = 80
					AND point_amount = 0
				""", Integer.class, couponId);
		assertThat(usageCount).isEqualTo(1);
	}

	@Test
	void rejectsWrongCouponTypeForShipmentFreeShipping() throws Exception {
		UserSession user = registerUser();
		seedPrizeBoxItems(user.userId(), 1);
		long addressId = createAddress(user.session());
		long couponId = seedCoupon("DISCOUNT", 80, 5, "ACTIVE");

		MvcResult listResult = mockMvc.perform(get("/api/account/prizes").session(user.session()))
				.andExpect(status().isOk())
				.andReturn();
		int prizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(listResult.getResponse().getContentAsString(), "$.items[0].id")).intValue();

		mockMvc.perform(post("/api/account/shipments")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d],"couponId":%d}
								""".formatted(addressId, prizeId, couponId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COUPON_NOT_REDEEMABLE"));
	}

	@Test
	void rejectsVipFreeShippingCouponUntilMemberQualifies() throws Exception {
		UserSession user = registerUser();
		seedPrizeBoxItems(user.userId(), 1);
		long addressId = createAddress(user.session());
		long couponId = seedCoupon("FREE_SHIPPING", 0, 5, "ACTIVE", "SILVER");

		MvcResult listResult = mockMvc.perform(get("/api/account/prizes").session(user.session()))
				.andExpect(status().isOk())
				.andReturn();
		int prizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(listResult.getResponse().getContentAsString(), "$.items[0].id")).intValue();

		mockMvc.perform(post("/api/account/shipments")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d],"couponId":%d}
								""".formatted(addressId, prizeId, couponId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("COUPON_VIP_REQUIRED"));

		jdbcTemplate.update("UPDATE draw_orders SET point_spent = 1200 WHERE user_id = ?", user.userId());

		mockMvc.perform(post("/api/account/shipments")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d],"couponId":%d}
								""".formatted(addressId, prizeId, couponId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shippingFee").value(0));
	}

	private UserSession registerUser() throws Exception {
		String email = "prizebox-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "戰利品測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		int userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).intValue();
		return new UserSession(userId, (MockHttpSession) result.getRequest().getSession(false));
	}

	private long createAddress(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/addresses")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "recipientName": "林小盒",
								  "phone": "0912345678",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "中正區",
								  "addressLine": "測試路 1 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private String seedPrizeBoxItems(long userId, int itemCount) {
		String slug = "prizebox-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '戰利品測試池', '戰利品測試', '戰利品 API 測試資料', NULL, NULL, 'MIXED',
					NULL, 'LuckyBox Test', 100, ?, 0, 'SOLD_OUT', ?, NULL, '測試出貨', '測試退換貨',
					0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, itemCount, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long prizeId = insertPrize(campaignId == null ? 0 : campaignId, itemCount, now);
		long orderId = insertDrawOrder(userId, campaignId == null ? 0 : campaignId, itemCount, now);
		for (int index = 1; index <= itemCount; index++) {
			long ticketId = insertTicket(campaignId == null ? 0 : campaignId, prizeId, slug, index, userId, orderId, now);
			long resultId = insertDrawResult(userId, campaignId == null ? 0 : campaignId, prizeId, ticketId, orderId, index, now);
			jdbcTemplate.update("""
					INSERT INTO user_prizes (
						user_id, campaign_id, prize_id, draw_result_id, status, shipment_id, expires_at, created_at, updated_at
					)
					VALUES (?, ?, ?, ?, 'IN_BOX', NULL, NULL, ?, ?)
					""", userId, campaignId, prizeId, resultId, now, now);
		}
		return slug;
	}

	private long insertPrize(long campaignId, int itemCount, String now) {
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '戰利品測試賞', '測試獎品', NULL, ?, 0, 1, 0, ?, ?)
				""", campaignId, itemCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private long insertDrawOrder(long userId, long campaignId, int itemCount, String now) {
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, status, idempotency_key,
					client_request_id, ip_address, user_agent, created_at, completed_at
				)
				VALUES (?, ?, ?, ?, 'COMPLETED', ?, NULL, NULL, NULL, ?, ?)
				""", userId, campaignId, itemCount, itemCount * 100, "prizebox-test-" + UUID.randomUUID(), now, now);
		Long orderId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return orderId == null ? 0 : orderId;
	}

	private long insertTicket(long campaignId, long prizeId, String slug, int index, long userId, long orderId, String now) {
		jdbcTemplate.update("""
				INSERT INTO kuji_tickets (
					campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
				)
				VALUES (?, ?, ?, 'DRAWN', ?, ?, ?, ?, ?)
				""", campaignId, prizeId, slug.toUpperCase() + "-" + String.format("%04d", index), orderId, userId, now, now, now);
		Long ticketId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return ticketId == null ? 0 : ticketId;
	}

	private long insertDrawResult(long userId, long campaignId, long prizeId, long ticketId, long orderId, int index, String now) {
		jdbcTemplate.update("""
				INSERT INTO draw_results (
					draw_order_id, ticket_id, prize_id, user_id, campaign_id, result_index, random_proof, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", orderId, ticketId, prizeId, userId, campaignId, index, "test-proof-" + index, now);
		Long resultId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return resultId == null ? 0 : resultId;
	}

	private long seedCoupon(String type, int value, int usageLimit, String status) {
		return seedCoupon(type, value, usageLimit, status, null);
	}

	private long seedCoupon(String type, int value, int usageLimit, String status, String vipTier) {
		String code = "PRIZEBOXCOUPON" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String now = Instant.now().toString();
		String startsAt = Instant.now().minusSeconds(60).toString();
		String endsAt = Instant.now().plusSeconds(3600).toString();
		jdbcTemplate.update("""
				INSERT INTO coupons (
					code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, 0, ?, 0, ?, ?, ?, ?, ?)
				""", code, type, vipTier, value, usageLimit, startsAt, endsAt, status, now, now);
		Long couponId = jdbcTemplate.queryForObject("SELECT id FROM coupons WHERE code = ?", Long.class, code);
		return couponId == null ? 0 : couponId;
	}

	private record UserSession(long userId, MockHttpSession session) {
	}
}
