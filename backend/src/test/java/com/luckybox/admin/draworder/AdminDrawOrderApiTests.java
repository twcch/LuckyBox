package com.luckybox.admin.draworder;

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
class AdminDrawOrderApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminDrawOrderTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'DrawOrder'
					AND entity_id IN (
						SELECT CAST(d.id AS TEXT)
						FROM draw_orders d
						JOIN kuji_campaigns c ON c.id = d.campaign_id
						WHERE c.slug LIKE 'admin-draw-test-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'User'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-draw-user-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-draw-user-%')
					OR (
						reference_type = 'DrawOrder'
						AND reference_id IN (
							SELECT d.id
							FROM draw_orders d
							JOIN kuji_campaigns c ON c.id = d.campaign_id
							WHERE c.slug LIKE 'admin-draw-test-%'
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admin-draw-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admin-draw-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admin-draw-test-%')
					OR user_id IN (SELECT id FROM users WHERE email LIKE 'admin-draw-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admin-draw-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admin-draw-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'admin-draw-test-%'");
		jdbcTemplate.update("""
				DELETE FROM payment_orders
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-draw-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-draw-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-draw-user-%'");
	}

	@Test
	void adminListsDrawOrdersWithMaskedUserEmailAndFilters() throws Exception {
		RegisteredUser user = registerUser();
		topUpCollector(user.session());
		String slug = seedCampaign(4);
		String idempotencyKey = UUID.randomUUID().toString();
		MvcResult drawResult = mockMvc.perform(post("/api/account/draw-orders")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, idempotencyKey)))
				.andExpect(status().isCreated())
				.andReturn();
		long orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(drawResult.getResponse().getContentAsString(), "$.id")).longValue();

		MvcResult listResult = mockMvc.perform(get("/api/admin/draw-orders")
						.session(loginAdmin())
						.param("status", "COMPLETED")
						.param("campaignSlug", slug)
						.param("q", user.email()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) orderId))
				.andExpect(jsonPath("$[0].userId").value((int) user.id()))
				.andExpect(jsonPath("$[0].userDisplayName").value("後台抽賞紀錄玩家"))
				.andExpect(jsonPath("$[0].maskedUserEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$[0].campaignSlug").value(slug))
				.andExpect(jsonPath("$[0].campaignTitle").value("後台抽賞紀錄測試池"))
				.andExpect(jsonPath("$[0].quantity").value(2))
				.andExpect(jsonPath("$[0].pointSpent").value(200))
				.andExpect(jsonPath("$[0].status").value("COMPLETED"))
				.andExpect(jsonPath("$[0].statusLabel").value("完成"))
				.andExpect(jsonPath("$[0].resultCount").value(2))
				.andExpect(jsonPath("$[0].prizeSummary").value("A賞 後台測試賞、A賞 後台測試賞"))
				.andReturn();
		assertThat(listResult.getResponse().getContentAsString()).doesNotContain(user.email());

		MvcResult detailResult = mockMvc.perform(get("/api/admin/draw-orders/{orderId}", orderId)
						.session(loginAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) orderId))
				.andExpect(jsonPath("$.userId").value((int) user.id()))
				.andExpect(jsonPath("$.maskedUserEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$.campaignSlug").value(slug))
				.andExpect(jsonPath("$.quantity").value(2))
				.andExpect(jsonPath("$.originalPointSpent").value(200))
				.andExpect(jsonPath("$.discountAmount").value(0))
				.andExpect(jsonPath("$.pointSpent").value(200))
				.andExpect(jsonPath("$.couponCode").doesNotExist())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
				.andExpect(jsonPath("$.results.length()").value(2))
				.andExpect(jsonPath("$.results[0].resultIndex").value(1))
				.andExpect(jsonPath("$.results[0].ticketSerialNumber").exists())
				.andExpect(jsonPath("$.results[0].prizeRank").value("A"))
				.andExpect(jsonPath("$.results[0].prizeName").value("後台測試賞"))
				.andExpect(jsonPath("$.results[0].lastPrize").value(false))
				.andExpect(jsonPath("$.results[0].randomProof").exists())
				.andExpect(jsonPath("$.ledgerRows.length()").value(2))
				.andExpect(jsonPath("$.ledgerRows[0].type").value("DRAW_SPEND"))
				.andExpect(jsonPath("$.ledgerRows[0].typeLabel").value("抽賞扣點"))
				.andExpect(jsonPath("$.ledgerRows[0].amount").value(-150))
				.andExpect(jsonPath("$.ledgerRows[0].pointKind").value("BONUS"))
				.andExpect(jsonPath("$.ledgerRows[0].pointKindLabel").value("紅利點"))
				.andExpect(jsonPath("$.ledgerRows[1].type").value("DRAW_SPEND"))
				.andExpect(jsonPath("$.ledgerRows[1].amount").value(-50))
				.andExpect(jsonPath("$.ledgerRows[1].pointKind").value("CASH"))
				.andExpect(jsonPath("$.ledgerRows[1].pointKindLabel").value("現金點"))
				.andReturn();
		assertThat(detailResult.getResponse().getContentAsString()).doesNotContain(user.email());
	}

	@Test
	void normalUserCannotReadAdminDrawOrders() throws Exception {
		MockHttpSession userSession = registerUser().session();
		mockMvc.perform(get("/api/admin/draw-orders").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
		mockMvc.perform(get("/api/admin/draw-orders/1").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsInvalidDrawOrderStatusFilter() throws Exception {
		mockMvc.perform(get("/api/admin/draw-orders")
						.session(loginAdmin())
						.param("status", "VOIDED"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DRAW_ORDER_STATUS"));
	}

	@Test
	void adminDrawOrderDetailReturnsNotFoundForMissingOrder() throws Exception {
		mockMvc.perform(get("/api/admin/draw-orders/999999").session(loginAdmin()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DRAW_ORDER_NOT_FOUND"));
	}

	private RegisteredUser registerUser() throws Exception {
		String email = "admin-draw-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台抽賞紀錄玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new RegisteredUser(userId, email, (MockHttpSession) result.getRequest().getSession(false));
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
		String slug = "admin-draw-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '後台抽賞紀錄測試池', '後台抽賞', '後台抽賞紀錄 API 測試資料', NULL, NULL,
					'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE', ?, NULL, '測試出貨',
					'測試退換貨', 0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, ticketCount, ticketCount, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long prizeId = insertPrize(campaignId == null ? 0 : campaignId, ticketCount, now);
		for (int index = 1; index <= ticketCount; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id,
						drawn_at, created_at, updated_at
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
				VALUES (?, 'A', '後台測試賞', '後台抽賞測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", campaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private record RegisteredUser(long id, String email, MockHttpSession session) {
	}
}
