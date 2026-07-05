package com.luckybox.admin.dashboard;

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
class AdminDashboardApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminDashboardTestData() {
		jdbcTemplate.update("""
				DELETE FROM wishes
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-dashboard-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM user_notifications
				WHERE reference_type = 'Shipment'
					AND reference_id IN (SELECT id FROM shipments WHERE recipient_snapshot LIKE '%Dashboard 測試收件人%')
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'dashboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM shipments
				WHERE recipient_snapshot LIKE '%Dashboard 測試收件人%'
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'dashboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'dashboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'dashboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders
				WHERE merchant_trade_no LIKE 'DASHBOARD-TEST-%'
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'dashboard-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'dashboard-test-%'");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE action = 'DASHBOARD_TEST_ACTIVITY'
				""");
	}

	@Test
	void adminCanReadOperationalDashboard() throws Exception {
		UserSession user = registerUser();
		long campaignId = seedCampaign();
		long prizeId = seedPrize(campaignId);
		long userPrizeId = seedUserPrize(user.userId(), campaignId, prizeId);
		long addressId = createAddress(user.session());
		createShipment(user.session(), addressId, userPrizeId);
		seedPaidPaymentOrder(user.userId());
		seedFailedPaymentOrder(user.userId());
		seedCompletedDrawOrder(user.userId(), campaignId);
		seedFailedDrawOrder(user.userId(), campaignId);
		seedPendingWish(user.userId());
		seedAuditLog();

		mockMvc.perform(get("/api/admin/dashboard").session(loginAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.metrics.length()").value(11))
				.andExpect(jsonPath("$.metrics[0].key").value("todayGmv"))
				.andExpect(jsonPath("$.metrics[0].label").value("今日營收"))
				.andExpect(jsonPath("$.metrics[0].value").exists())
				.andExpect(jsonPath("$.metrics[1].key").value("todayDraws"))
				.andExpect(jsonPath("$.metrics[1].value").exists())
				.andExpect(jsonPath("$.metrics[3].key").value("todayActiveUsers"))
				.andExpect(jsonPath("$.metrics[3].value").exists())
				.andExpect(jsonPath("$.metrics[5].key").value("nearSoldCampaigns"))
				.andExpect(jsonPath("$.metrics[5].label").value("即將售完賞池"))
				.andExpect(jsonPath("$.metrics[5].value").exists())
				.andExpect(jsonPath("$.metrics[6].key").value("requestedShipments"))
				.andExpect(jsonPath("$.metrics[6].label").value("未出貨"))
				.andExpect(jsonPath("$.metrics[6].value").exists())
				.andExpect(jsonPath("$.metrics[7].key").value("supportQueue"))
				.andExpect(jsonPath("$.metrics[7].label").value("客服待處理"))
				.andExpect(jsonPath("$.metrics[7].value").exists())
				.andExpect(jsonPath("$.metrics[8].key").value("failedPayments"))
				.andExpect(jsonPath("$.metrics[8].value").exists())
				.andExpect(jsonPath("$.metrics[9].key").value("drawAlerts"))
				.andExpect(jsonPath("$.metrics[9].label").value("異常抽賞告警"))
				.andExpect(jsonPath("$.metrics[9].value").exists())
				.andExpect(jsonPath("$.productMetrics.length()").value(14))
				.andExpect(jsonPath("$.productMetrics[0].key").value("visitorToRegistration"))
				.andExpect(jsonPath("$.productMetrics[0].value").value("N/A"))
				.andExpect(jsonPath("$.productMetrics[1].key").value("registrationToTopUp"))
				.andExpect(jsonPath("$.productMetrics[1].value").exists())
				.andExpect(jsonPath("$.productMetrics[2].key").value("topUpToDraw"))
				.andExpect(jsonPath("$.productMetrics[2].value").exists())
				.andExpect(jsonPath("$.productMetrics[5].key").value("arppu"))
				.andExpect(jsonPath("$.productMetrics[8].key").value("prizeShipmentRequestRate"))
				.andExpect(jsonPath("$.productMetrics[10].key").value("refundCompensationRate"))
				.andExpect(jsonPath("$.productMetrics[11].key").value("paymentFailureRate"))
				.andExpect(jsonPath("$.productMetrics[12].key").value("drawApiErrorRate"))
				.andExpect(jsonPath("$.productMetrics[13].key").value("drawApiP95Latency"))
				.andExpect(jsonPath("$.requestedShipments[0].userEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$.requestedShipments[0].itemCount").value(1))
				.andExpect(jsonPath("$.recentActivities[0].action").value("DASHBOARD_TEST_ACTIVITY"));
	}

	@Test
	void normalUserCannotReadOperationalDashboard() throws Exception {
		UserSession user = registerUser();
		mockMvc.perform(get("/api/admin/dashboard").session(user.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	private UserSession registerUser() throws Exception {
		String email = "admin-dashboard-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Dashboard 測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new UserSession(userId, email, (MockHttpSession) result.getRequest().getSession(false));
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

	private long createAddress(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/addresses")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "recipientName": "Dashboard 測試收件人",
								  "phone": "0912345678",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "中正區",
								  "addressLine": "Dashboard 測試路 8 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private void createShipment(MockHttpSession session, long addressId, long userPrizeId) throws Exception {
		mockMvc.perform(post("/api/account/shipments")
						.session(session)
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d]}
								""".formatted(addressId, userPrizeId)))
				.andExpect(status().isCreated());
	}

	private long seedCampaign() {
		String slug = "dashboard-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, 'Dashboard 測試池', 'Dashboard 測試', 'Dashboard API 測試資料', NULL, NULL, 'MIXED',
					NULL, 'LuckyBox Test', 100, 10, 1, 'LIVE', ?, NULL, '測試出貨', '測試退換貨',
					0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return campaignId == null ? 0 : campaignId;
	}

	private long seedPrize(long campaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', 'Dashboard 測試賞', '測試獎品', NULL, 1, 0, 1, 0, ?, ?)
				""", campaignId, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private long seedUserPrize(long userId, long campaignId, long prizeId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO user_prizes (
					user_id, campaign_id, prize_id, draw_result_id, status, shipment_id, expires_at, created_at, updated_at
				)
				VALUES (?, ?, ?, NULL, 'IN_BOX', NULL, NULL, ?, ?)
				""", userId, campaignId, prizeId, now, now);
		Long userPrizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return userPrizeId == null ? 0 : userPrizeId;
	}

	private void seedPaidPaymentOrder(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO payment_orders (
					user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
					status, provider_payload, paid_at, created_at, updated_at
				)
				VALUES (?, 'MOCK', ?, 600, 600, 0, 'PAID', '{}', ?, ?, ?)
				""", userId, "DASHBOARD-TEST-PAID-" + UUID.randomUUID(), now, now, now);
	}

	private void seedFailedPaymentOrder(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO payment_orders (
					user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
					status, provider_payload, paid_at, created_at, updated_at
				)
				VALUES (?, 'MOCK', ?, 300, 300, 0, 'FAILED', '{}', NULL, ?, ?)
				""", userId, "DASHBOARD-TEST-FAILED-" + UUID.randomUUID(), now, now);
	}

	private void seedCompletedDrawOrder(long userId, long campaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, status, idempotency_key,
					client_request_id, ip_address, user_agent, created_at, completed_at
				)
				VALUES (?, ?, 3, 300, 'COMPLETED', ?, NULL, NULL, NULL, ?, ?)
				""", userId, campaignId, "dashboard-test-" + UUID.randomUUID(), now, now);
	}

	private void seedFailedDrawOrder(long userId, long campaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, status, idempotency_key,
					client_request_id, ip_address, user_agent, created_at, completed_at
				)
				VALUES (?, ?, 1, 100, 'FAILED', ?, NULL, NULL, NULL, ?, NULL)
				""", userId, campaignId, "dashboard-test-failed-" + UUID.randomUUID(), now);
	}

	private void seedPendingWish(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO wishes (user_id, content, status, moderated_at, created_at, updated_at)
				VALUES (?, '希望上架 Dashboard 測試賞池', 'PENDING', NULL, ?, ?)
				""", userId, now, now);
	}

	private void seedAuditLog() {
		jdbcTemplate.update("""
				INSERT INTO audit_logs (
					actor_id, actor_role, action, entity_type, entity_id, before_state, after_state, ip_address, created_at
				)
				VALUES (NULL, 'SYSTEM', 'DASHBOARD_TEST_ACTIVITY', 'Dashboard', 'test', NULL, '{}', NULL, ?)
				""", Instant.now().toString());
	}

	private record UserSession(long userId, String email, MockHttpSession session) {
	}
}
