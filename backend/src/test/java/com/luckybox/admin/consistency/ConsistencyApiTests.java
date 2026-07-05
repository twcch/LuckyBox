package com.luckybox.admin.consistency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class ConsistencyApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ConsistencyService consistencyService;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM audit_logs WHERE action = 'DATA_INCONSISTENCY_DETECTED' AND after_state LIKE '%cons-test-%'");
		jdbcTemplate.update("DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'cons-test-%')");
		jdbcTemplate.update("DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'cons-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'cons-test-%'");
	}

	@Test
	void consistentCampaignProducesNoFinding() throws Exception {
		String slug = seedConsistentCampaign(5);
		MockHttpSession adminSession = loginAdmin();

		MvcResult result = mockMvc.perform(get("/api/admin/consistency").session(adminSession))
				.andExpect(status().isOk())
				.andReturn();

		List<String> slugs = JsonPath.read(result.getResponse().getContentAsString(), "$.findings[*].slug");
		assertThat(slugs).doesNotContain(slug);
	}

	@Test
	void detectsRemainingTicketMismatch() throws Exception {
		String slug = seedConsistentCampaign(5);
		// Corrupt the counter without touching tickets: remaining now disagrees with AVAILABLE count.
		jdbcTemplate.update("UPDATE kuji_campaigns SET remaining_tickets = remaining_tickets - 2 WHERE slug = ?", slug);
		MockHttpSession adminSession = loginAdmin();

		MvcResult result = mockMvc.perform(get("/api/admin/consistency").session(adminSession))
				.andExpect(status().isOk())
				.andReturn();

		List<Map<String, Object>> findings = JsonPath.read(result.getResponse().getContentAsString(), "$.findings");
		boolean found = findings.stream().anyMatch(f ->
				slug.equals(f.get("slug")) && "REMAINING_TICKET_MISMATCH".equals(f.get("code")));
		assertThat(found).isTrue();
	}

	@Test
	void auditScanRecordsFindingsToAuditLog() {
		String slug = seedConsistentCampaign(5);
		jdbcTemplate.update("UPDATE kuji_campaigns SET remaining_tickets = remaining_tickets - 2 WHERE slug = ?", slug);

		int findings = consistencyService.auditScan();

		assertThat(findings).isGreaterThanOrEqualTo(1);
		Integer logged = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'DATA_INCONSISTENCY_DETECTED' AND after_state LIKE ?
				""", Integer.class, "%" + slug + "%");
		assertThat(logged).isGreaterThanOrEqualTo(1);
	}

	@Test
	void nonAdminIsForbidden() throws Exception {
		MockHttpSession userSession = registerUser();

		mockMvc.perform(get("/api/admin/consistency").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
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
		String email = "cons-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "一致性測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private String seedConsistentCampaign(int tickets) {
		String slug = "cons-test-" + UUID.randomUUID().toString().substring(0, 8);
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
				""", slug, "一致性測試池", "一致性測試", "一致性稽核測試資料", tickets, tickets, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '一致性測試賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
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
