package com.luckybox.admin.shipment;

import static org.assertj.core.api.Assertions.assertThat;
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
class AdminShipmentResolveApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM user_notifications
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ship-resolve-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'ship-resolve-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ship-resolve-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM shipments WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ship-resolve-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'shipresolve-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'shipresolve-test-%'");
		jdbcTemplate.update("""
				DELETE FROM user_addresses WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ship-resolve-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ship-resolve-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'ship-resolve-%'");
	}

	@Test
	void returnedResolutionSendsPrizeBackToBoxAndNotifies() throws Exception {
		UserSession user = registerUser();
		long prizeId = seedPrize(user.userId());
		long addressId = createAddress(user.session());
		long shipmentId = createShipment(user.session(), addressId, prizeId);
		MockHttpSession admin = loginAdmin();
		ship(admin, shipmentId);

		mockMvc.perform(post("/api/admin/shipments/{id}/resolve", shipmentId)
						.session(admin)
						.contentType("application/json")
						.content("{\"resolution\":\"RETURNED\",\"reason\":\"包裹破損退回\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RETURNED"))
				.andExpect(jsonPath("$.adminNote").value(org.hamcrest.Matchers.containsString("退回：包裹破損退回")));

		// 戰利品回到 IN_BOX 且解除出貨連結，可重新申請出貨。
		String prizeStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM user_prizes WHERE id = ?", String.class, prizeId);
		Object shipmentLink = jdbcTemplate.queryForMap("SELECT shipment_id FROM user_prizes WHERE id = ?", prizeId)
				.get("shipment_id");
		assertThat(prizeStatus).isEqualTo("IN_BOX");
		assertThat(shipmentLink).isNull();

		Integer audit = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_SHIPMENT_RESOLVED' AND entity_type = 'Shipment' AND entity_id = ?
				""", Integer.class, String.valueOf(shipmentId));
		assertThat(audit).isEqualTo(1);

		Integer notified = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM user_notifications
				WHERE user_id = ? AND type = 'SHIPMENT_RETURNED'
				""", Integer.class, user.userId());
		assertThat(notified).isEqualTo(1);

		mockMvc.perform(get("/api/admin/shipments")
						.session(admin)
						.param("status", "RETURNED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) shipmentId))
				.andExpect(jsonPath("$[0].status").value("RETURNED"));
	}

	@Test
	void exchangedResolutionMarksPrizeExchanged() throws Exception {
		UserSession user = registerUser();
		long prizeId = seedPrize(user.userId());
		long addressId = createAddress(user.session());
		long shipmentId = createShipment(user.session(), addressId, prizeId);
		MockHttpSession admin = loginAdmin();
		ship(admin, shipmentId);

		mockMvc.perform(post("/api/admin/shipments/{id}/resolve", shipmentId)
						.session(admin)
						.contentType("application/json")
						.content("{\"resolution\":\"EXCHANGED\",\"reason\":\"商品瑕疵換貨\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EXCHANGED"));

		String prizeStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM user_prizes WHERE id = ?", String.class, prizeId);
		assertThat(prizeStatus).isEqualTo("EXCHANGED");
	}

	@Test
	void rejectsResolutionOnNonDispatchedMissingReasonBadValueAndNonAdmin() throws Exception {
		UserSession user = registerUser();
		long prizeId = seedPrize(user.userId());
		long addressId = createAddress(user.session());
		long shipmentId = createShipment(user.session(), addressId, prizeId);
		MockHttpSession admin = loginAdmin();

		// 尚未出貨（REQUESTED）不可退回/換貨。
		mockMvc.perform(post("/api/admin/shipments/{id}/resolve", shipmentId)
						.session(admin)
						.contentType("application/json")
						.content("{\"resolution\":\"RETURNED\",\"reason\":\"尚未出貨\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SHIPMENT_NOT_RESOLVABLE"));

		ship(admin, shipmentId);

		// 缺原因。
		mockMvc.perform(post("/api/admin/shipments/{id}/resolve", shipmentId)
						.session(admin)
						.contentType("application/json")
						.content("{\"resolution\":\"RETURNED\",\"reason\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SHIPMENT_RESOLUTION_REASON_REQUIRED"));

		// 不合法處理方式。
		mockMvc.perform(post("/api/admin/shipments/{id}/resolve", shipmentId)
						.session(admin)
						.contentType("application/json")
						.content("{\"resolution\":\"DESTROY\",\"reason\":\"亂填\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SHIPMENT_RESOLUTION"));

		// 一般會員。
		mockMvc.perform(post("/api/admin/shipments/{id}/resolve", shipmentId)
						.session(user.session())
						.contentType("application/json")
						.content("{\"resolution\":\"RETURNED\",\"reason\":\"我自己退\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	private void ship(MockHttpSession admin, long shipmentId) throws Exception {
		mockMvc.perform(patch("/api/admin/shipments/{id}", shipmentId)
						.session(admin)
						.contentType("application/json")
						.content("""
								{"status":"SHIPPED","carrier":"黑貓宅急便","trackingNumber":"TA999","adminNote":"已交寄"}
								"""))
				.andExpect(status().isOk());
	}

	private UserSession registerUser() throws Exception {
		String email = "ship-resolve-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!","displayName":"退換貨測試玩家"}
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
						.content("{\"email\":\"admin@luckybox.local\",\"password\":\"ChangeMe123!\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long createAddress(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/addresses")
						.session(session)
						.contentType("application/json")
						.content("""
								{"recipientName":"退換貨測試收件人","phone":"0912345678","postalCode":"100",
								 "city":"台北市","district":"中正區","addressLine":"退換貨測試路 1 號","defaultAddress":true}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private long createShipment(MockHttpSession session, long addressId, long prizeId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/shipments")
						.session(session)
						.contentType("application/json")
						.content("{\"addressId\":%d,\"prizeIds\":[%d]}".formatted(addressId, prizeId)))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private long seedPrize(long userId) {
		String slug = "shipresolve-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '退換貨測試池', '退換貨測試', '退換貨 API 測試資料', NULL, NULL, 'MIXED',
					NULL, 'LuckyBox Test', 100, 1, 0, 'SOLD_OUT', ?, NULL, '測試出貨', '測試退換貨',
					0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '退換貨測試賞', '測試獎品', NULL, 1, 0, 1, 0, ?, ?)
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
