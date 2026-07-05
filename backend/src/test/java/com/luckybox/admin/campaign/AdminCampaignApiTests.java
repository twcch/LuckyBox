package com.luckybox.admin.campaign;

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
class AdminCampaignApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminCampaignTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE action IN (
					'ADMIN_PRIZE_CREATED',
					'ADMIN_PRIZE_UPDATED',
					'ADMIN_TICKETS_GENERATED',
					'ADMIN_TICKETS_GENERATION_BLOCKED',
					'ADMIN_CAMPAIGN_PUBLISHED',
					'ADMIN_CAMPAIGN_PAUSED',
					'ADMIN_CAMPAIGN_CORRECTION_VERSION_CREATED',
					'ADMIN_CAMPAIGN_SENSITIVE_CHANGE_BLOCKED',
					'ADMIN_PRIZE_CHANGE_BLOCKED'
				)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'Campaign'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM kuji_campaigns
						WHERE slug LIKE 'admincampaign-test-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admincampaign-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'admincampaign-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'admincampaign-test-%'");
	}

	@Test
	void adminCanCreateListAndUpdateCampaign() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);

		MvcResult createResult = mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(slug, "後台測試賞池", "DRAFT", 12, true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.slug").value(slug))
				.andExpect(jsonPath("$.title").value("後台測試賞池"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.sourceTypeLabel").value("自營混套賞"))
				.andExpect(jsonPath("$.commercialUseConfirmed").value(true))
				.andExpect(jsonPath("$.officialLicenseConfirmed").value(false))
				.andExpect(jsonPath("$.rightsNotice").value("商品來源與圖片素材由營運確認可於平台展示。"))
				.andExpect(jsonPath("$.ageRestricted").value(false))
				.andExpect(jsonPath("$.totalTickets").value(12))
				.andExpect(jsonPath("$.remainingTickets").value(12))
				.andExpect(jsonPath("$.soldTickets").value(0))
				.andExpect(jsonPath("$.hasLastPrize").value(true))
				.andReturn();
		long campaignId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get("/api/admin/campaigns")
						.session(adminSession)
						.param("status", "DRAFT")
						.param("q", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) campaignId))
				.andExpect(jsonPath("$[0].slug").value(slug));

		mockMvc.perform(patch("/api/admin/campaigns/{campaignId}", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(slug, "後台測試賞池 Updated", "SCHEDULED", 16, false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) campaignId))
				.andExpect(jsonPath("$.title").value("後台測試賞池 Updated"))
				.andExpect(jsonPath("$.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.commercialUseConfirmed").value(true))
				.andExpect(jsonPath("$.ageRestricted").value(false))
				.andExpect(jsonPath("$.totalTickets").value(16))
				.andExpect(jsonPath("$.remainingTickets").value(16))
				.andExpect(jsonPath("$.hasLastPrize").value(false));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE entity_type = 'Campaign'
					AND entity_id = ?
					AND action IN ('ADMIN_CAMPAIGN_CREATED', 'ADMIN_CAMPAIGN_UPDATED')
				""", Integer.class, String.valueOf(campaignId));
		assertThat(auditCount).isEqualTo(2);
	}

	@Test
	void adminCanCreateGkCampaignSourceType() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-gk-" + UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug,
								"GK 來源測試賞池",
								"DRAFT",
								6,
								false,
								120,
								"SERVER_RANDOM",
								"",
								"GK",
								true,
								false,
								"GK 商品來源與圖片素材已由營運確認可商用。",
								false,
								null,
								"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceType").value("GK"))
				.andExpect(jsonPath("$.sourceTypeLabel").value("GK 賞"))
				.andExpect(jsonPath("$.rightsNotice").value("GK 商品來源與圖片素材已由營運確認可商用。"));
	}

	@Test
	void normalUserCannotUseAdminCampaignApi() throws Exception {
		MockHttpSession userSession = registerUser();
		mockMvc.perform(get("/api/admin/campaigns").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void adminCanSortCampaignList() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String lowPriceSlug = "admincampaign-test-sort-low-" + suffix;
		String highPriceSlug = "admincampaign-test-sort-high-" + suffix;
		String lowRemainingSlug = "admincampaign-test-sort-few-" + suffix;
		seedCampaign(lowPriceSlug, "排序測試 低價", 90, 20, 20, "LIVE");
		seedCampaign(highPriceSlug, "排序測試 高價", 180, 20, 12, "LIVE");
		seedCampaign(lowRemainingSlug, "排序測試 剩少", 120, 20, 3, "LIVE");

		mockMvc.perform(get("/api/admin/campaigns")
						.session(adminSession)
						.param("q", "排序測試")
						.param("sort", "priceDesc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].slug").value(highPriceSlug))
				.andExpect(jsonPath("$[0].pricePerDraw").value(180));

		mockMvc.perform(get("/api/admin/campaigns")
						.session(adminSession)
						.param("q", "排序測試")
						.param("sort", "remainingAsc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].slug").value(lowRemainingSlug))
				.andExpect(jsonPath("$[0].remainingTickets").value(3));
	}

	@Test
	void invalidCampaignSortReturnsBadRequest() throws Exception {
		MockHttpSession adminSession = loginAdmin();

		mockMvc.perform(get("/api/admin/campaigns")
						.session(adminSession)
						.param("sort", "DROP_TABLE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_SORT"));
	}

	@Test
	void duplicateSlugReturnsConflict() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		seedCampaign(slug, 10, 10);

		mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(slug, "重複代碼", "DRAFT", 8, false)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_SLUG_EXISTS"));
	}

	@Test
	void updateCannotReduceTotalTicketsBelowSoldTickets() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = seedCampaign(slug, 10, 6);

		mockMvc.perform(patch("/api/admin/campaigns/{campaignId}", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(slug, "售出保護", "LIVE", 3, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TOTAL_TICKETS_TOO_LOW"))
				.andExpect(jsonPath("$.details.soldTickets").value(4));
	}

	@Test
	void adminManagesPrizesAndGeneratesTickets() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);

		MvcResult prizeResult = mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "A 測試賞", 2, 1, false)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rank").value("A"))
				.andExpect(jsonPath("$.originalQuantity").value(2))
				.andExpect(jsonPath("$.remainingQuantity").value(2))
				.andExpect(jsonPath("$.generatedTickets").value(0))
				.andReturn();
		long prizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(prizeResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("LAST", "最後賞", 1, 2, true)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.lastPrize").value(true));

		mockMvc.perform(get("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPrizeQuantity").value(2))
				.andExpect(jsonPath("$.remainingPrizeQuantity").value(2))
				.andExpect(jsonPath("$.generatedTickets").value(0))
				.andExpect(jsonPath("$.prizes.length()").value(2));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(2))
				.andExpect(jsonPath("$.totalTickets").value(2))
				.andExpect(jsonPath("$.availableTickets").value(2))
				.andExpect(jsonPath("$.prizeCount").value(1));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(0))
				.andExpect(jsonPath("$.totalTickets").value(2));

		mockMvc.perform(patch("/api/admin/campaigns/{campaignId}/prizes/{prizeId}", campaignId, prizeId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "A 測試賞更新", 3, 1, false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("A 測試賞更新"))
				.andExpect(jsonPath("$.originalQuantity").value(3))
				.andExpect(jsonPath("$.remainingQuantity").value(2));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(1))
				.andExpect(jsonPath("$.totalTickets").value(3))
				.andExpect(jsonPath("$.availableTickets").value(3));

		mockMvc.perform(get("/api/admin/campaigns")
						.session(adminSession)
						.param("q", slug))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].totalTickets").value(3))
				.andExpect(jsonPath("$[0].remainingTickets").value(3))
				.andExpect(jsonPath("$[0].prizeCount").value(2));

		Integer ticketCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM kuji_tickets
				WHERE campaign_id = ?
				""", Integer.class, campaignId);
		assertThat(ticketCount).isEqualTo(3);
	}

	@Test
	void adminCanListCampaignTicketsAndPrizeLibrary() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-library-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);
		long prizeId = createPrizeAndTickets(adminSession, campaignId, 3);
		Long drawnUserId = jdbcTemplate.queryForObject(
				"SELECT id FROM users WHERE email = 'admin@luckybox.local'",
				Long.class);
		Long drawnTicketId = jdbcTemplate.queryForObject("""
				SELECT id
				FROM kuji_tickets
				WHERE campaign_id = ?
				ORDER BY id
				LIMIT 1
				""", Long.class, campaignId);
		jdbcTemplate.update("""
				UPDATE kuji_tickets
				SET status = 'DRAWN', draw_id = 9001, drawn_by_user_id = ?, drawn_at = ?, updated_at = ?
				WHERE id = ?
				""", drawnUserId, Instant.now().toString(), Instant.now().toString(), drawnTicketId);

		mockMvc.perform(get("/api/admin/campaigns/{campaignId}/tickets", campaignId)
						.session(adminSession)
						.param("q", slug.toUpperCase().replaceAll("[^A-Z0-9]+", "_")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].campaignId").value((int) campaignId))
				.andExpect(jsonPath("$[0].prizeId").value((int) prizeId))
				.andExpect(jsonPath("$[0].status").value("DRAWN"))
				.andExpect(jsonPath("$[0].statusLabel").value("已抽出"))
				.andExpect(jsonPath("$[0].drawId").value(9001))
				.andExpect(jsonPath("$[0].drawnByEmail").value(org.hamcrest.Matchers.containsString("***")));

		mockMvc.perform(get("/api/admin/campaigns/{campaignId}/tickets", campaignId)
						.session(adminSession)
						.param("status", "AVAILABLE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].status").value("AVAILABLE"));

		mockMvc.perform(get("/api/admin/prizes")
						.session(adminSession)
						.param("q", slug)
						.param("rank", "A")
						.param("lastPrize", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) prizeId))
				.andExpect(jsonPath("$[0].campaignSlug").value(slug))
				.andExpect(jsonPath("$[0].campaignStatusLabel").value("草稿"))
				.andExpect(jsonPath("$[0].generatedTickets").value(3))
				.andExpect(jsonPath("$[0].drawnTickets").value(1))
				.andExpect(jsonPath("$[0].availableTickets").value(2));
	}

	@Test
	void invalidTicketAndPrizeLibraryFiltersReturnBadRequest() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-invalid-filter-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);

		mockMvc.perform(get("/api/admin/campaigns/{campaignId}/tickets", campaignId)
						.session(adminSession)
						.param("status", "BROKEN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TICKET_STATUS"));

		mockMvc.perform(get("/api/admin/prizes")
						.session(adminSession)
						.param("campaignStatus", "BROKEN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_STATUS"));
	}

	@Test
	void cannotReducePrizeQuantityBelowGeneratedTickets() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);
		MvcResult prizeResult = mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "A 測試賞", 2, 1, false)))
				.andExpect(status().isCreated())
				.andReturn();
		long prizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(prizeResult.getResponse().getContentAsString(), "$.id")).longValue();
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(2));

		mockMvc.perform(patch("/api/admin/campaigns/{campaignId}/prizes/{prizeId}", campaignId, prizeId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "A 測試賞", 1, 1, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PRIZE_QUANTITY_BELOW_TICKETS"));
	}

	@Test
	void adminPublishesAndPausesCampaign() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "可發布賞", 2, 1, false)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(2));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LIVE"))
				.andExpect(jsonPath("$.remainingTickets").value(2));

		mockMvc.perform(get("/api/campaigns")
						.param("q", "獎項管理")
						.param("status", "LIVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].slug").value(slug));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/pause", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAUSED"));

		mockMvc.perform(get("/api/campaigns")
						.param("q", "獎項管理"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE entity_type = 'Campaign'
					AND entity_id = ?
					AND action IN ('ADMIN_CAMPAIGN_PUBLISHED', 'ADMIN_CAMPAIGN_PAUSED')
				""", Integer.class, String.valueOf(campaignId));
		assertThat(auditCount).isEqualTo(2);
	}

	@Test
	void publishRequiresCommercialUseConfirmation() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		MvcResult createResult = mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug,
								"商用素材檢查賞池",
								"DRAFT",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"",
								"MIXED",
								false,
								false,
								"尚未確認素材商用授權。",
								false,
								null,
								"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.commercialUseConfirmed").value(false))
				.andReturn();
		long campaignId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
		createPrizeAndTickets(adminSession, campaignId, 2);

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_COMMERCIAL_USE_NOT_CONFIRMED"));
	}

	@Test
	void officialCampaignRequiresLicenseConfirmationBeforePublish() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		MvcResult createResult = mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug,
								"官方授權檢查賞池",
								"DRAFT",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"",
								"OFFICIAL",
								true,
								false,
								"",
								false,
								null,
								"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sourceType").value("OFFICIAL"))
				.andExpect(jsonPath("$.officialLicenseConfirmed").value(false))
				.andReturn();
		long campaignId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
		createPrizeAndTickets(adminSession, campaignId, 2);

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_OFFICIAL_LICENSE_NOT_CONFIRMED"));

		mockMvc.perform(patch("/api/admin/campaigns/{campaignId}", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug,
								"官方授權檢查賞池",
								"DRAFT",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"admin-campaign-" + slug,
								"OFFICIAL",
								true,
								true,
								"官方商品授權佐證已由營運留存。",
								false,
								null,
								"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.officialLicenseConfirmed").value(true));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LIVE"));
	}

	@Test
	void ageRestrictedCampaignRequiresMinimumAgeAndVerificationNote() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug + "-missing-age",
								"年齡限制缺最低年齡",
								"DRAFT",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"",
								"MIXED",
								true,
								false,
								"",
								true,
								null,
								"結帳前檢查身分證明。")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AGE_RESTRICTION_MINIMUM_REQUIRED"));

		mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug + "-missing-note",
								"年齡限制缺驗證",
								"DRAFT",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"",
								"MIXED",
								true,
								false,
								"",
								true,
								18,
								"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AGE_VERIFICATION_NOTE_REQUIRED"));

		mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug + "-ok",
								"年齡限制完整賞池",
								"DRAFT",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"",
								"MIXED",
								true,
								false,
								"18+ 商品，結帳前會確認資格。",
								true,
								18,
								"結帳前檢查會員生日與必要身分證明。")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ageRestricted").value(true))
				.andExpect(jsonPath("$.minimumAge").value(18))
				.andExpect(jsonPath("$.ageVerificationNote").value("結帳前檢查會員生日與必要身分證明。"));
	}

	@Test
	void adminCreatesCorrectionVersionFromLiveCampaign() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "A 修正測試賞", 2, 1, false)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LIVE"));

		MvcResult correctionResult = mockMvc.perform(post("/api/admin/campaigns/{campaignId}/correction-version", campaignId)
						.session(adminSession))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.startsWith(slug + "-correction-")))
				.andExpect(jsonPath("$.title").value("獎項管理測試池 修正版"))
				.andExpect(jsonPath("$.prizeCount").value(1))
				.andReturn();
		long correctionId = ((Number) com.jayway.jsonpath.JsonPath
				.read(correctionResult.getResponse().getContentAsString(), "$.id")).longValue();

		String originalStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM kuji_campaigns WHERE id = ?", String.class, campaignId);
		assertThat(originalStatus).isEqualTo("PAUSED");
		Integer correctionTickets = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ?", Integer.class, correctionId);
		assertThat(correctionTickets).isZero();
		Integer correctionPrizes = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM prizes WHERE campaign_id = ?", Integer.class, correctionId);
		assertThat(correctionPrizes).isEqualTo(1);
		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE action = 'ADMIN_CAMPAIGN_CORRECTION_VERSION_CREATED'
					AND entity_type = 'Campaign'
					AND entity_id = ?
				""", Integer.class, String.valueOf(campaignId));
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	void publishedCampaignBlocksSensitiveMasterDataChangesAndAuditsAttempt() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		PublishedCampaign published = createAndPublishCampaign(adminSession, slug);

		MvcResult result = mockMvc.perform(patch("/api/admin/campaigns/{campaignId}", published.campaignId())
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug,
								"公開後敏感修改",
								"LIVE",
								3,
								false,
								999,
								"HASH_COMMIT_REVEAL",
								"tampered-seed")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_SENSITIVE_FIELDS_LOCKED"))
				.andReturn();
		assertThat(result.getResponse().getContentAsString())
				.contains("pricePerDraw", "totalTickets", "fairnessMode", "seedHash");

		MapRow unchanged = jdbcTemplate.queryForObject("""
				SELECT price_per_draw, total_tickets, status, fairness_mode, seed_hash
				FROM kuji_campaigns
				WHERE id = ?
				""", (rs, rowNum) -> new MapRow(
				rs.getInt("price_per_draw"),
				rs.getInt("total_tickets"),
				rs.getString("status"),
				rs.getString("fairness_mode"),
				rs.getString("seed_hash")), published.campaignId());
		assertThat(unchanged.pricePerDraw()).isEqualTo(120);
		assertThat(unchanged.totalTickets()).isEqualTo(2);
		assertThat(unchanged.status()).isEqualTo("LIVE");
		assertThat(unchanged.fairnessMode()).isEqualTo("SERVER_RANDOM");
		assertThat(unchanged.seedHash()).isEqualTo("admin-campaign-" + slug);

		String auditState = jdbcTemplate.queryForObject("""
				SELECT after_state
				FROM audit_logs
				WHERE action = 'ADMIN_CAMPAIGN_SENSITIVE_CHANGE_BLOCKED'
					AND entity_type = 'Campaign'
					AND entity_id = ?
				ORDER BY id DESC
				LIMIT 1
				""", String.class, String.valueOf(published.campaignId()));
		assertThat(auditState).contains(slug, "pricePerDraw", "totalTickets", "fairnessMode", "seedHash");
	}

	@Test
	void publishedCampaignStillAllowsPresentationUpdates() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		PublishedCampaign published = createAndPublishCampaign(adminSession, slug);

		mockMvc.perform(patch("/api/admin/campaigns/{campaignId}", published.campaignId())
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(
								slug,
								"公開後可更新說明",
								"LIVE",
								2,
								false,
								120,
								"SERVER_RANDOM",
								"admin-campaign-" + slug)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("公開後可更新說明"))
				.andExpect(jsonPath("$.status").value("LIVE"))
				.andExpect(jsonPath("$.totalTickets").value(2));

		Integer updateAuditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE action = 'ADMIN_CAMPAIGN_UPDATED'
					AND entity_type = 'Campaign'
					AND entity_id = ?
				""", Integer.class, String.valueOf(published.campaignId()));
		assertThat(updateAuditCount).isEqualTo(1);
	}

	@Test
	void publishedCampaignBlocksPrizeChangesAndAuditsAttempt() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		PublishedCampaign published = createAndPublishCampaign(adminSession, slug);

		mockMvc.perform(patch(
						"/api/admin/campaigns/{campaignId}/prizes/{prizeId}",
						published.campaignId(),
						published.prizeId())
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "公開後改獎項", 2, 1, false)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_PRIZES_LOCKED"))
				.andExpect(jsonPath("$.details.status").value("LIVE"));

		String prizeName = jdbcTemplate.queryForObject(
				"SELECT name FROM prizes WHERE id = ?",
				String.class,
				published.prizeId());
		assertThat(prizeName).isEqualTo("可發布賞");

		Integer blockedAuditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE action = 'ADMIN_PRIZE_CHANGE_BLOCKED'
					AND entity_type = 'Prize'
					AND entity_id = ?
				""", Integer.class, String.valueOf(published.prizeId()));
		assertThat(blockedAuditCount).isEqualTo(1);
	}

	@Test
	void cannotPublishWithoutGeneratedTickets() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_TICKETS_REQUIRED"))
				.andExpect(jsonPath("$.details.totalTickets").value(0))
				.andExpect(jsonPath("$.details.availableTickets").value(0));
	}

	@Test
	void adminCanDryRunCampaign() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "Dry Run 測試賞", 3, 1, false)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(3));

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/dry-run", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.campaignId").value((int) campaignId))
				.andExpect(jsonPath("$.requestedQuantity").value(3))
				.andExpect(jsonPath("$.availableTickets").value(3))
				.andExpect(jsonPath("$.totalTickets").value(3))
				.andExpect(jsonPath("$.results.length()").value(3))
				.andExpect(jsonPath("$.results[0].rank").value("A"))
				.andExpect(jsonPath("$.results[0].prizeName").value("Dry Run 測試賞"));
	}

	@Test
	void dryRunRequiresGeneratedTickets() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/dry-run", campaignId)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_DRY_RUN_TICKETS_REQUIRED"))
				.andExpect(jsonPath("$.details.totalTickets").value(0))
				.andExpect(jsonPath("$.details.availableTickets").value(0));
	}

	@Test
	void cannotPauseDraftCampaign() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String slug = "admincampaign-test-" + UUID.randomUUID().toString().substring(0, 8);
		long campaignId = createCampaign(adminSession, slug, 0);

		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/pause", campaignId)
						.session(adminSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_ACTIVE"));
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
		String email = "admin-campaign-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台賞池測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long seedCampaign(String slug, int totalTickets, int remainingTickets) {
		return seedCampaign(slug, "Admin Campaign 測試池", 100, totalTickets, remainingTickets, "LIVE");
	}

	private long seedCampaign(String slug, String title, int pricePerDraw, int totalTickets, int remainingTickets, String status) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, ?, '後台測試', 'Admin Campaign API 測試資料', NULL, NULL, 'MIXED',
					NULL, 'LuckyBox Test', ?, ?, ?, ?, ?, NULL, '測試出貨', '測試退換貨',
					0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, title, pricePerDraw, totalTickets, remainingTickets, status, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return campaignId == null ? 0 : campaignId;
	}

	private long createCampaign(MockHttpSession adminSession, String slug, int totalTickets) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/admin/campaigns")
						.session(adminSession)
						.contentType("application/json")
						.content(campaignJson(slug, "獎項管理測試池", "DRAFT", totalTickets, false)))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private PublishedCampaign createAndPublishCampaign(MockHttpSession adminSession, String slug) throws Exception {
		long campaignId = createCampaign(adminSession, slug, 0);
		long prizeId = createPrizeAndTickets(adminSession, campaignId, 2);
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/publish", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("LIVE"));
		return new PublishedCampaign(campaignId, prizeId);
	}

	private long createPrizeAndTickets(MockHttpSession adminSession, long campaignId, int quantity) throws Exception {
		MvcResult prizeResult = mockMvc.perform(post("/api/admin/campaigns/{campaignId}/prizes", campaignId)
						.session(adminSession)
						.contentType("application/json")
						.content(prizeJson("A", "可發布賞", quantity, 1, false)))
				.andExpect(status().isCreated())
				.andReturn();
		long prizeId = ((Number) com.jayway.jsonpath.JsonPath
				.read(prizeResult.getResponse().getContentAsString(), "$.id")).longValue();
		mockMvc.perform(post("/api/admin/campaigns/{campaignId}/tickets/generate", campaignId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.generatedCount").value(quantity));
		return prizeId;
	}

	private static String campaignJson(String slug, String title, String status, int totalTickets, boolean hasLastPrize) {
		return campaignJson(slug, title, status, totalTickets, hasLastPrize, 120, "SERVER_RANDOM", "");
	}

	private static String campaignJson(
			String slug,
			String title,
			String status,
			int totalTickets,
			boolean hasLastPrize,
			int pricePerDraw,
			String fairnessMode,
			String seedHash) {
		return campaignJson(
				slug,
				title,
				status,
				totalTickets,
				hasLastPrize,
				pricePerDraw,
				fairnessMode,
				seedHash,
				"MIXED",
				true,
				false,
				"商品來源與圖片素材由營運確認可於平台展示。",
				false,
				null,
				"");
	}

	private static String campaignJson(
			String slug,
			String title,
			String status,
			int totalTickets,
			boolean hasLastPrize,
			int pricePerDraw,
			String fairnessMode,
			String seedHash,
			String sourceType,
			boolean commercialUseConfirmed,
			boolean officialLicenseConfirmed,
			String rightsNotice,
			boolean ageRestricted,
			Integer minimumAge,
			String ageVerificationNote) {
		String minimumAgeJson = minimumAge == null ? "null" : minimumAge.toString();
		return """
				{
				  "slug": "%s",
				  "title": "%s",
				  "subtitle": "後台賞池 MVP",
				  "description": "這是後台賞池管理 API 測試資料。",
				  "coverImageUrl": "https://images.example.com/cover.png",
				  "bannerImageUrl": "",
				  "sourceType": "%s",
				  "commercialUseConfirmed": %s,
				  "officialLicenseConfirmed": %s,
				  "rightsNotice": "%s",
				  "ageRestricted": %s,
				  "minimumAge": %s,
				  "ageVerificationNote": "%s",
				  "ipName": "LuckyBox",
				  "brandName": "LuckyBox Test",
				  "pricePerDraw": %d,
				  "totalTickets": %d,
				  "status": "%s",
				  "salesStartAt": "2026-06-13T10:00:00",
				  "salesEndAt": "",
				  "shippingNote": "測試出貨說明",
				  "returnPolicyNote": "測試退換貨說明",
				  "hasLastPrize": %s,
				  "lastPrizeRule": "%s",
				  "fairnessMode": "%s",
				  "seedHash": "%s"
				}
				""".formatted(
				slug,
				title,
				sourceType,
				commercialUseConfirmed,
				officialLicenseConfirmed,
				rightsNotice == null ? "" : rightsNotice,
				ageRestricted,
				minimumAgeJson,
				ageVerificationNote == null ? "" : ageVerificationNote,
				pricePerDraw,
				totalTickets,
				status,
				hasLastPrize,
				hasLastPrize ? "最後一抽可獲得測試最後賞" : "",
				fairnessMode,
				seedHash);
	}

	private static String prizeJson(String rank, String name, int originalQuantity, int sortOrder, boolean lastPrize) {
		return """
				{
				  "rank": "%s",
				  "name": "%s",
				  "description": "後台獎項測試資料",
				  "imageUrl": "https://images.example.com/prize.png",
				  "originalQuantity": %d,
				  "sortOrder": %d,
				  "lastPrize": %s
				}
				""".formatted(rank, name, originalQuantity, sortOrder, lastPrize);
	}

	private record PublishedCampaign(long campaignId, long prizeId) {
	}

	private record MapRow(
			int pricePerDraw,
			int totalTickets,
			String status,
			String fairnessMode,
			String seedHash) {
	}
}
