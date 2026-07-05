package com.luckybox.campaign;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteCampaignApiTestData() {
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'campaign-db-source-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'campaign-db-source-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'campaign-db-source-%'");
	}

	@Test
	void listsSeededCampaigns() throws Exception {
		mockMvc.perform(get("/api/campaigns"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content.length()").value(3))
				.andExpect(jsonPath("$.content[0].slug").value("star-collection-vol-1"))
				.andExpect(jsonPath("$.content[0].coverImageUrl").value(nullValue()))
				.andExpect(jsonPath("$.content[0].remainingTickets").value(80))
				.andExpect(jsonPath("$.content[0].rareHint").value("S賞剩 1"));
	}

	@Test
	void filtersSortsAndPaginatesCampaigns() throws Exception {
		mockMvc.perform(get("/api/campaigns")
						.param("q", "卡牌")
						.param("sourceType", "CARD")
						.param("status", "LIVE")
						.param("sort", "priceDesc")
						.param("page", "0")
						.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.content[0].slug").value("card-supply-selection"))
				.andExpect(jsonPath("$.sort").value("priceDesc"))
				.andExpect(jsonPath("$.sourceType").value("CARD"))
				.andExpect(jsonPath("$.status").value("LIVE"));
	}

	@Test
	void returnsCampaignDetailWithPrizesAndProbabilities() throws Exception {
		mockMvc.perform(get("/api/campaigns/star-collection-vol-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("星光收藏盒 Vol.1"))
				.andExpect(jsonPath("$.coverImageUrl").value(nullValue()))
				.andExpect(jsonPath("$.bannerImageUrl").value(nullValue()))
				.andExpect(jsonPath("$.prizes.length()").value(6))
				.andExpect(jsonPath("$.prizes[0].probability").value(1.25))
				.andExpect(jsonPath("$.prizes[5].lastPrize").value(true));
	}

	@Test
	void publicCampaignRemainingCountsUseAvailableTicketsWhenCountersAreStale() throws Exception {
		String slug = seedCampaignWithStaleCounters();

		mockMvc.perform(get("/api/campaigns")
						.param("q", "DB真相測試池"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].slug").value(slug))
				.andExpect(jsonPath("$.content[0].remainingTickets").value(2))
				.andExpect(jsonPath("$.content[0].remainingRate").value(50.0))
				.andExpect(jsonPath("$.content[0].rareHint").value("A賞剩 2"));

		mockMvc.perform(get("/api/campaigns/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remainingTickets").value(2))
				.andExpect(jsonPath("$.remainingRate").value(50.0))
				.andExpect(jsonPath("$.prizes[0].remainingQuantity").value(2))
				.andExpect(jsonPath("$.prizes[0].probability").value(100.0))
				.andExpect(jsonPath("$.prizes[1].remainingQuantity").value(0))
				.andExpect(jsonPath("$.prizes[1].probability").value(0.0));
	}

	@Test
	void returnsCampaignComplianceAndAgeDisclosure() throws Exception {
		String slug = seedAgeRestrictedCampaign();

		mockMvc.perform(get("/api/campaigns")
						.param("q", "年齡揭露測試池"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].slug").value(slug))
				.andExpect(jsonPath("$.content[0].ageRestricted").value(true))
				.andExpect(jsonPath("$.content[0].minimumAge").value(18))
				.andExpect(jsonPath("$.content[0].ageRestrictionLabel").value("18+"));

		mockMvc.perform(get("/api/campaigns/{slug}", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rightsNotice").value("18+ 商品來源與素材已由營運確認可商用。"))
				.andExpect(jsonPath("$.ageRestricted").value(true))
				.andExpect(jsonPath("$.minimumAge").value(18))
				.andExpect(jsonPath("$.ageRestrictionLabel").value("18+"))
				.andExpect(jsonPath("$.ageVerificationNote").value("結帳前檢查會員生日與必要身分證明。"));
	}

	@Test
	void returnsStructuredErrorForMissingCampaign() throws Exception {
		mockMvc.perform(get("/api/campaigns/missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/api/campaigns/missing"));
	}

	private String seedCampaignWithStaleCounters() {
		String slug = "campaign-db-source-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, 'DB真相測試池', '剩餘數測試', '公開 API 應以 ticket 狀態為準', NULL, NULL,
					'MIXED', NULL, 'LuckyBox Test', 100, 4, 4, 'LIVE', ?, NULL, '測試出貨',
					'測試退換貨', 0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long aPrizeId = insertPrize(campaignId == null ? 0 : campaignId, "A", "A 賞", 2, 0, 1, now);
		long bPrizeId = insertPrize(campaignId == null ? 0 : campaignId, "B", "B 賞", 2, 2, 2, now);
		for (int index = 1; index <= 2; index++) {
			insertTicket(campaignId == null ? 0 : campaignId, aPrizeId, slug, index, "AVAILABLE", now);
		}
		for (int index = 3; index <= 4; index++) {
			insertTicket(campaignId == null ? 0 : campaignId, bPrizeId, slug, index, "DRAWN", now);
		}
		return slug;
	}

	private String seedAgeRestrictedCampaign() {
		String slug = "campaign-db-source-age-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					commercial_use_confirmed, official_license_confirmed, rights_notice,
					age_restricted, minimum_age, age_verification_note,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '年齡揭露測試池', '18+ 測試', '公開 API 應揭露年齡限制', NULL, NULL,
					'MIXED', 1, 0, '18+ 商品來源與素材已由營運確認可商用。', 1, 18,
					'結帳前檢查會員生日與必要身分證明。', NULL, 'LuckyBox Test', 100, 2, 2,
					'LIVE', ?, NULL, '測試出貨', '測試退換貨', 0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long prizeId = insertPrize(campaignId == null ? 0 : campaignId, "A", "18+ A 賞", 2, 2, 1, now);
		for (int index = 1; index <= 2; index++) {
			insertTicket(campaignId == null ? 0 : campaignId, prizeId, slug, index, "AVAILABLE", now);
		}
		return slug;
	}

	private long insertPrize(long campaignId, String rank, String name, int originalQuantity, int remainingQuantity,
			int sortOrder, String now) {
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, ?, ?, '公開剩餘數測試獎品', NULL, ?, ?, ?, 0, ?, ?)
				""", campaignId, rank, name, originalQuantity, remainingQuantity, sortOrder, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	private void insertTicket(long campaignId, long prizeId, String slug, int index, String status, String now) {
		jdbcTemplate.update("""
				INSERT INTO kuji_tickets (
					campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?, ?)
				""", campaignId, prizeId, slug.toUpperCase() + "-" + String.format("%04d", index), status, now, now);
	}
}
