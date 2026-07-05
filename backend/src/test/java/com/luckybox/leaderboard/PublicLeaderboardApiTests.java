package com.luckybox.leaderboard;

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

@SpringBootTest
@AutoConfigureMockMvc
class PublicLeaderboardApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteLeaderboardTestData() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'leaderboard-user-%')
					OR (
						reference_type = 'DrawOrder'
						AND reference_id IN (
							SELECT d.id
							FROM draw_orders d
							JOIN kuji_campaigns c ON c.id = d.campaign_id
							WHERE c.slug LIKE 'leaderboard-test-%'
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'leaderboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'leaderboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'leaderboard-test-%')
					OR user_id IN (SELECT id FROM users WHERE email LIKE 'leaderboard-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'leaderboard-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'leaderboard-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'leaderboard-test-%'");
		jdbcTemplate.update("""
				DELETE FROM payment_orders
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'leaderboard-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'leaderboard-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'leaderboard-user-%'");
	}

	@Test
	void publicLeaderboardReturnsMaskedLiveDrawsAndPopularCampaigns() throws Exception {
		RegisteredUser user = registerUser();
		topUpCollector(user.session());
		String slug = seedCampaign(5);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(slug, UUID.randomUUID())))
				.andExpect(status().isCreated());

		MvcResult leaderboard = mockMvc.perform(get("/api/leaderboard")
						.param("liveLimit", "5")
						.param("popularLimit", "5")
						.param("luckyLimit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liveDraws[0].maskedDisplayName").value("Lu**"))
				.andExpect(jsonPath("$.liveDraws[0].campaignSlug").value(slug))
				.andExpect(jsonPath("$.liveDraws[0].campaignTitle").value("公開榜單測試池"))
				.andExpect(jsonPath("$.liveDraws[0].prizeRank").value("A"))
				.andExpect(jsonPath("$.liveDraws[0].prizeName").value("榜單測試賞"))
				.andExpect(jsonPath("$.popularCampaigns[0].slug").value(slug))
				.andExpect(jsonPath("$.popularCampaigns[0].drawCount").value(2))
				.andExpect(jsonPath("$.popularCampaigns[0].uniqueDrawers").value(1))
				.andExpect(jsonPath("$.popularCampaigns[0].soldTickets").value(2))
				.andExpect(jsonPath("$.popularCampaigns[0].soldRate").value(40.0))
				.andExpect(jsonPath("$.popularCampaigns[0].rareHint").value("A賞剩 3"))
				.andExpect(jsonPath("$.luckyMembers").isArray())
				.andExpect(jsonPath("$.generatedAt").exists())
				.andReturn();

		String body = leaderboard.getResponse().getContentAsString();
		assertThat(body).doesNotContain(user.email());

		// 歐氣榜：抽到的兩張皆為 A 賞（高稀有度），該會員應出現在歐氣榜並計入 2 次幸運紀錄。
		List<java.util.Map<String, Object>> luckyMembers = com.jayway.jsonpath.JsonPath
				.read(body, "$.luckyMembers");
		java.util.Map<String, Object> luckyFan = luckyMembers.stream()
				.filter(member -> "Lu**".equals(member.get("displayName")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("歐氣榜應包含抽中高稀有度的測試會員"));
		assertThat(((Number) luckyFan.get("luckyWins")).intValue()).isEqualTo(2);
		assertThat(((Number) luckyFan.get("topRankWins")).intValue()).isEqualTo(0);
		assertThat(((Number) luckyFan.get("lastPrizeWins")).intValue()).isEqualTo(0);
		assertThat(((Number) luckyFan.get("position")).intValue()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void campaignDrawHistoryReturnsOnlyTheRequestedCampaign() throws Exception {
		RegisteredUser user = registerUser();
		topUpCollector(user.session());
		String targetSlug = seedCampaign(4);
		String otherSlug = seedCampaign(4);

		mockMvc.perform(post("/api/account/draw-orders")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(otherSlug, UUID.randomUUID())))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/account/draw-orders")
						.session(user.session())
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(targetSlug, UUID.randomUUID())))
				.andExpect(status().isCreated());

		MvcResult history = mockMvc.perform(get("/api/leaderboard/campaigns/{slug}/draws", targetSlug)
						.param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.draws.length()").value(2))
				.andExpect(jsonPath("$.draws[0].maskedDisplayName").value("Lu**"))
				.andExpect(jsonPath("$.draws[0].campaignSlug").value(targetSlug))
				.andExpect(jsonPath("$.draws[1].campaignSlug").value(targetSlug))
				.andExpect(jsonPath("$.generatedAt").exists())
				.andReturn();

		String response = history.getResponse().getContentAsString();
		assertThat(response).doesNotContain(otherSlug);
		assertThat(response).doesNotContain(user.email());
	}

	private RegisteredUser registerUser() throws Exception {
		String email = "leaderboard-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Lucky Fan"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return new RegisteredUser(email, (MockHttpSession) result.getRequest().getSession(false));
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
		String slug = "leaderboard-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '公開榜單測試池', '抽況榜單', '公開 leaderboard API 測試資料', NULL, NULL,
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
				VALUES (?, 'A', '榜單測試賞', '公開榜單測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", campaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private record RegisteredUser(String email, MockHttpSession session) {
	}
}
