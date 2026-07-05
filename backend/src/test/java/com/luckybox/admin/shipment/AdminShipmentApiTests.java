package com.luckybox.admin.shipment;

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
class AdminShipmentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminShipmentTestData() {
		jdbcTemplate.update("""
				DELETE FROM user_notifications
				WHERE reference_type = 'Shipment'
					AND body LIKE '%出貨單 #%'
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'adminshipment-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM shipments
				WHERE recipient_snapshot LIKE '%後台測試收件人%'
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'adminshipment-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'adminshipment-test-%'");
	}

	@Test
	void adminListsAndUpdatesShipment() throws Exception {
		UserSession user = registerUser();
		long userPrizeId = seedPrize(user.userId());
		long addressId = createAddress(user.session());
		long shipmentId = createShipment(user.session(), addressId, userPrizeId);
		MockHttpSession adminSession = loginAdmin();

		mockMvc.perform(get("/api/admin/shipments")
						.session(adminSession)
						.param("status", "REQUESTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) shipmentId))
				.andExpect(jsonPath("$[0].userEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$[0].recipientName").value("後台測試收件人"))
				.andExpect(jsonPath("$[0].items[0].status").value("SHIPMENT_REQUESTED"));

		mockMvc.perform(patch("/api/admin/shipments/{shipmentId}", shipmentId)
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{
								  "status": "SHIPPED",
								  "carrier": "黑貓宅急便",
								  "trackingNumber": "TA123456789",
								  "adminNote": "已交寄"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SHIPPED"))
				.andExpect(jsonPath("$.carrier").value("黑貓宅急便"))
				.andExpect(jsonPath("$.trackingNumber").value("TA123456789"))
				.andExpect(jsonPath("$.items[0].status").value("SHIPPED"));

		mockMvc.perform(get("/api/account/prizes")
						.session(user.session())
						.param("status", "SHIPPED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value((int) userPrizeId));

		MvcResult notificationsResult = mockMvc.perform(get("/api/account/notifications")
						.session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(1))
				.andExpect(jsonPath("$.items[0].type").value("SHIPMENT_SHIPPED"))
				.andExpect(jsonPath("$.items[0].title").value("出貨已交寄"))
				.andExpect(jsonPath("$.items[0].referenceType").value("Shipment"))
				.andExpect(jsonPath("$.items[0].referenceId").value((int) shipmentId))
				.andExpect(jsonPath("$.items[0].readAt").doesNotExist())
				.andReturn();
		int notificationId = ((Number) com.jayway.jsonpath.JsonPath
				.read(notificationsResult.getResponse().getContentAsString(), "$.items[0].id")).intValue();

		mockMvc.perform(patch("/api/account/notifications/{notificationId}/read", notificationId)
						.session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(notificationId))
				.andExpect(jsonPath("$.readAt").exists());

		mockMvc.perform(get("/api/account/notifications")
						.session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE action = 'ADMIN_SHIPMENT_UPDATED' AND entity_id = ?
				""", Integer.class, String.valueOf(shipmentId));
		org.assertj.core.api.Assertions.assertThat(auditCount).isEqualTo(1);
	}

	@Test
	void normalUserCannotUseAdminShipmentApi() throws Exception {
		UserSession user = registerUser();
		mockMvc.perform(get("/api/admin/shipments").session(user.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void shippedStatusRequiresTrackingFields() throws Exception {
		UserSession user = registerUser();
		long userPrizeId = seedPrize(user.userId());
		long addressId = createAddress(user.session());
		long shipmentId = createShipment(user.session(), addressId, userPrizeId);
		MockHttpSession adminSession = loginAdmin();

		mockMvc.perform(patch("/api/admin/shipments/{shipmentId}", shipmentId)
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"SHIPPED"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TRACKING_REQUIRED"));
	}

	private UserSession registerUser() throws Exception {
		String email = "admin-shipment-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台出貨測試玩家"
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
								  "recipientName": "後台測試收件人",
								  "phone": "0912345678",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "中正區",
								  "addressLine": "後台測試路 1 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private long createShipment(MockHttpSession session, long addressId, long userPrizeId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/shipments")
						.session(session)
						.contentType("application/json")
						.content("""
								{"addressId":%d,"prizeIds":[%d]}
								""".formatted(addressId, userPrizeId)))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private long seedPrize(long userId) {
		String slug = "adminshipment-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '後台出貨測試池', '後台測試', '後台出貨 API 測試資料', NULL, NULL, 'MIXED',
					NULL, 'LuckyBox Test', 100, 1, 0, 'SOLD_OUT', ?, NULL, '測試出貨', '測試退換貨',
					0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '後台測試賞', '測試獎品', NULL, 1, 0, 1, 0, ?, ?)
				""", campaignId, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		jdbcTemplate.update("""
				INSERT INTO user_prizes (
					user_id, campaign_id, prize_id, draw_result_id, status, shipment_id, expires_at, created_at, updated_at
				)
				VALUES (?, ?, ?, NULL, 'IN_BOX', NULL, NULL, ?, ?)
				""", userId, campaignId, prizeId, now, now);
		Long userPrizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return userPrizeId == null ? 0 : userPrizeId;
	}

	private record UserSession(long userId, String email, MockHttpSession session) {
	}
}
