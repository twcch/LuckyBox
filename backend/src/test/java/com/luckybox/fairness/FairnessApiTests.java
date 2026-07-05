package com.luckybox.fairness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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
class FairnessApiTests {

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
					WHERE c.slug LIKE 'fair-test-%'
				  )
				""");
		jdbcTemplate.update("DELETE FROM user_prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'fair-test-%')");
		jdbcTemplate.update("DELETE FROM draw_results WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'fair-test-%')");
		jdbcTemplate.update("DELETE FROM draw_orders WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'fair-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'fair-test-%')");
		jdbcTemplate.update("DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'fair-test-%')");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'fair-test-%'");
	}

	@Test
	void commitRevealDrawProducesVerifiableProofs() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCommitRevealCampaign(3);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":3,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.results.length()").value(3));

		MvcResult fairnessResult = mockMvc.perform(get("/api/campaigns/{slug}/fairness", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fairnessMode").value("HASH_COMMIT_REVEAL"))
				.andExpect(jsonPath("$.revealed").value(true))
				.andExpect(jsonPath("$.drawnTickets").value(3))
				.andExpect(jsonPath("$.draws.length()").value(3))
				.andReturn();

		String body = fairnessResult.getResponse().getContentAsString();
		String seedHash = JsonPath.read(body, "$.seedHash");
		String revealedSeed = JsonPath.read(body, "$.revealedSeed");

		assertThat(revealedSeed).isNotBlank();
		assertThat(Fairness.sha256Hex(revealedSeed)).isEqualTo(seedHash);

		List<String> proofs = JsonPath.read(body, "$.draws[*].randomProof");
		assertThat(proofs).hasSize(3);
		for (String proof : proofs) {
			assertThat(proof).startsWith("hmac-sha256:");
			String[] parts = proof.split(":");
			assertThat(parts).hasSize(4);
			String nonce = parts[1] + ":" + parts[2];
			String hmac = parts[3];
			assertThat(Fairness.hmacSha256Hex(revealedSeed, nonce)).isEqualTo(hmac);
		}
	}

	@Test
	void seedNotRevealedUntilSoldOut() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCommitRevealCampaign(3);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/campaigns/{slug}/fairness", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.seedHash").isNotEmpty())
				.andExpect(jsonPath("$.revealed").value(false))
				.andExpect(jsonPath("$.drawnTickets").value(1))
				.andExpect(jsonPath("$.draws.length()").value(1));
	}

	@Test
	void lastPrizeIsCountedConsistentlyInFairnessSummary() throws Exception {
		MockHttpSession session = registerUser();
		topUpCollector(session);
		String slug = seedCommitRevealCampaignWithLastPrize(2);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.results.length()").value(3));

		// drawnTickets must equal the draws list length (3 = 2 regular + 1 last prize), not total-remaining (2).
		mockMvc.perform(get("/api/campaigns/{slug}/fairness", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.revealed").value(true))
				.andExpect(jsonPath("$.drawnTickets").value(3))
				.andExpect(jsonPath("$.draws.length()").value(3));
	}

	private String seedCommitRevealCampaignWithLastPrize(int regularTickets) {
		String slug = "fair-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, server_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 1, ?, 'HASH_COMMIT_REVEAL', NULL, NULL, NULL, ?, ?)
				""", slug, "公平性最後賞測試池", "公平性最後賞", "commit/reveal 最後賞測試",
				regularTickets, regularTickets, now, "最後一張觸發最後賞", now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '一般賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", resolvedCampaignId, regularTickets, regularTickets, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		long resolvedPrizeId = prizeId == null ? 0 : prizeId;
		for (int index = 1; index <= regularTickets; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", resolvedCampaignId, resolvedPrizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'LAST', '最後賞', '售完觸發', NULL, 1, 1, 99, 1, ?, ?)
				""", resolvedCampaignId, now, now);
		return slug;
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "fair-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "公平性測試玩家"
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

	private String seedCommitRevealCampaign(int tickets) {
		String slug = "fair-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, server_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 0, NULL, 'HASH_COMMIT_REVEAL', NULL, NULL, NULL, ?, ?)
				""", slug, "公平性測試池", "公平性測試", "commit/reveal 測試資料", tickets, tickets, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '公平性測試賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
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
