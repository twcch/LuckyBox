package com.luckybox.admin.auditsummary;

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
class AuditSummaryApiTests {

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
					WHERE c.slug LIKE 'sum-test-%'
				  )
				""");
		jdbcTemplate.update("DELETE FROM user_prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sum-test-%')");
		jdbcTemplate.update("DELETE FROM draw_results WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sum-test-%')");
		jdbcTemplate.update("DELETE FROM draw_orders WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sum-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sum-test-%')");
		jdbcTemplate.update("DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'sum-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'sum-test-%'");
	}

	@Test
	void summarizesDrawOutcome() throws Exception {
		MockHttpSession userSession = registerUser();
		topUpCollector(userSession);
		String slug = seedCampaign(3);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(userSession)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated());

		MockHttpSession adminSession = loginAdmin();
		mockMvc.perform(get("/api/admin/audit-summary/{slug}", slug).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(slug))
				.andExpect(jsonPath("$.drawnTickets").value(2))
				.andExpect(jsonPath("$.remainingTickets").value(1))
				.andExpect(jsonPath("$.totalDrawResults").value(2))
				.andExpect(jsonPath("$.uniqueDrawers").value(1))
				.andExpect(jsonPath("$.totalOrders").value(1))
				.andExpect(jsonPath("$.hasLastPrize").value(false))
				.andExpect(jsonPath("$.lastPrizeAwarded").value(false))
				.andExpect(jsonPath("$.prizeDistribution.length()").value(1))
				.andExpect(jsonPath("$.prizeDistribution[0].rank").value("A"))
				.andExpect(jsonPath("$.prizeDistribution[0].originalQuantity").value(3))
				.andExpect(jsonPath("$.prizeDistribution[0].drawnCount").value(2))
				.andExpect(jsonPath("$.prizeDistribution[0].remainingQuantity").value(1));
	}

	@Test
	void nonAdminIsForbidden() throws Exception {
		String slug = seedCampaign(3);
		MockHttpSession userSession = registerUser();

		mockMvc.perform(get("/api/admin/audit-summary/{slug}", slug).session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void unknownCampaignReturnsNotFound() throws Exception {
		MockHttpSession adminSession = loginAdmin();

		mockMvc.perform(get("/api/admin/audit-summary/{slug}", "sum-test-does-not-exist").session(adminSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
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

	private MockHttpSession registerUser() throws Exception {
		String email = "sum-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "稽核摘要測試玩家"
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
		String slug = "sum-test-" + UUID.randomUUID().toString().substring(0, 8);
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
				""", slug, "稽核摘要測試池", "稽核摘要測試", "audit summary 測試資料", tickets, tickets, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '稽核摘要測試賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
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
