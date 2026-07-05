package com.luckybox.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AdminMemberDetailApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
					OR entity_id IN (SELECT CAST(id AS TEXT) FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM member_notes WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
					OR author_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'memberdetail-prize-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'memberdetail-prize-%'");
		jdbcTemplate.update("""
				DELETE FROM user_addresses WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'memberdetail-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'memberdetail-%'");
	}

	@Test
	void adminSeesMaskedMemberDetailByDefaultAndFullPiiRequiresRevealAudit() throws Exception {
		String email = "memberdetail-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = registerWithPhone(email, "0912345678");
		addAddress(member);
		topUpCollector(member);
		long userId = userIdByEmail(email);

		MockHttpSession admin = loginAdmin();
		MvcResult maskedResult = mockMvc.perform(get("/api/admin/users/{userId}", userId).session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.piiRevealed").value(false))
				.andExpect(jsonPath("$.email").value("me***@e***.com"))
				.andExpect(jsonPath("$.phone").value("***678"))
				.andExpect(jsonPath("$.addresses[0].recipientName").value("收***"))
				.andExpect(jsonPath("$.addresses[0].phone").value("***678"))
				.andExpect(jsonPath("$.addresses[0].addressLine").value("忠孝東***"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.statusLabel").value("啟用"))
				.andExpect(jsonPath("$.cashPointBalance").value(1000))
				.andExpect(jsonPath("$.bonusPointBalance").value(150))
				.andExpect(jsonPath("$.availableBalance").value(1150))
				.andExpect(jsonPath("$.paidOrderCount").value(1))
				.andExpect(jsonPath("$.recentLedger[0].typeLabel").isNotEmpty())
				.andReturn();
		String maskedBody = maskedResult.getResponse().getContentAsString();
		assertThat(maskedBody).doesNotContain(email)
				.doesNotContain("0912345678")
				.doesNotContain("忠孝東路一段1號");

		Integer noRevealAuditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_MEMBER_DETAIL_VIEWED' AND entity_type = 'User' AND entity_id = ?
				""", Integer.class, String.valueOf(userId));
		assertThat(noRevealAuditCount).isZero();

		mockMvc.perform(get("/api/admin/users/{userId}", userId)
						.session(admin)
						.param("reveal", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.piiRevealed").value(true))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.phone").value("0912345678"))
				.andExpect(jsonPath("$.addresses[0].recipientName").value("收件人甲"))
				.andExpect(jsonPath("$.addresses[0].phone").value("0912345678"))
				.andExpect(jsonPath("$.addresses[0].addressLine").value("忠孝東路一段1號"));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_MEMBER_DETAIL_VIEWED' AND entity_type = 'User' AND entity_id = ?
				""", Integer.class, String.valueOf(userId));
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	void adminSeesRecentPrizesInMemberDetail() throws Exception {
		String email = "memberdetail-" + UUID.randomUUID() + "@example.com";
		registerWithPhone(email, "0911222444");
		long userId = userIdByEmail(email);
		seedUserPrize(userId);
		MockHttpSession admin = loginAdmin();

		mockMvc.perform(get("/api/admin/users/{userId}", userId).session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prizeCount").value(1))
				.andExpect(jsonPath("$.recentPrizes[0].campaignSlug").value(org.hamcrest.Matchers.startsWith("memberdetail-prize-")))
				.andExpect(jsonPath("$.recentPrizes[0].campaignTitle").value("會員戰利品測試池"))
				.andExpect(jsonPath("$.recentPrizes[0].prizeRank").value("A"))
				.andExpect(jsonPath("$.recentPrizes[0].prizeName").value("客服可查戰利品"))
				.andExpect(jsonPath("$.recentPrizes[0].status").value("IN_BOX"))
				.andExpect(jsonPath("$.recentPrizes[0].statusLabel").value("可出貨"));
	}

	@Test
	void adminCanAddMemberNoteAndItShowsInDetail() throws Exception {
		String email = "memberdetail-" + UUID.randomUUID() + "@example.com";
		registerWithPhone(email, "0911222333");
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		mockMvc.perform(post("/api/admin/users/{userId}/notes", userId)
						.session(admin)
						.contentType("application/json")
						.content("{\"content\":\"來電詢問出貨進度，已協助查詢。\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content").value("來電詢問出貨進度，已協助查詢。"))
				.andExpect(jsonPath("$.authorName").value("LuckyBox Admin"));

		mockMvc.perform(get("/api/admin/users/{userId}", userId).session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notes[0].content").value("來電詢問出貨進度，已協助查詢。"))
				.andExpect(jsonPath("$.notes[0].authorName").value("LuckyBox Admin"));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_MEMBER_NOTE_ADDED' AND entity_type = 'User' AND entity_id = ?
				""", Integer.class, String.valueOf(userId));
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	void blankMemberNoteIsRejectedAndNonAdminCannotAdd() throws Exception {
		String email = "memberdetail-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = registerWithPhone(email, "0911222333");
		long userId = userIdByEmail(email);

		MockHttpSession admin = loginAdmin();
		mockMvc.perform(post("/api/admin/users/{userId}/notes", userId)
						.session(admin)
						.contentType("application/json")
						.content("{\"content\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_MEMBER_NOTE"));

		mockMvc.perform(post("/api/admin/users/{userId}/notes", userId)
						.session(member)
						.contentType("application/json")
						.content("{\"content\":\"我自己加的備註\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void normalUserCannotViewMemberDetail() throws Exception {
		String email = "memberdetail-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = registerWithPhone(email, "0900000000");
		long userId = userIdByEmail(email);

		mockMvc.perform(get("/api/admin/users/{userId}", userId).session(member))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void unknownMemberReturnsNotFound() throws Exception {
		MockHttpSession admin = loginAdmin();
		mockMvc.perform(get("/api/admin/users/{userId}", 999999999L).session(admin))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	private MockHttpSession registerWithPhone(String email, String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!","displayName":"會員詳情測試","phone":"%s"}
								""".formatted(email, phone)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void addAddress(MockHttpSession session) throws Exception {
		mockMvc.perform(post("/api/account/addresses")
						.session(session)
						.contentType("application/json")
						.content("""
								{"recipientName":"收件人甲","phone":"0912345678","postalCode":"100",
								 "city":"臺北市","district":"中正區","addressLine":"忠孝東路一段1號","defaultAddress":true}
								"""))
				.andExpect(status().isCreated());
	}

	private void topUpCollector(MockHttpSession session) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("{\"planId\":\"collector\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
	}

	private void seedUserPrize(long userId) {
		String slug = "memberdetail-prize-" + UUID.randomUUID().toString().substring(0, 8);
		String now = java.time.Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '會員戰利品測試池', '客服查詢', '會員詳情戰利品查詢測試', NULL, NULL, 'MIXED',
					NULL, 'LuckyBox Test', 100, 1, 0, 'SOLD_OUT', ?, NULL, '測試出貨', '測試退換貨',
					0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '客服可查戰利品', '測試獎品', NULL, 1, 0, 1, 0, ?, ?)
				""", campaignId, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		jdbcTemplate.update("""
				INSERT INTO user_prizes (
					user_id, campaign_id, prize_id, draw_result_id, status, shipment_id, expires_at, created_at, updated_at
				)
				VALUES (?, ?, ?, NULL, 'IN_BOX', NULL, NULL, ?, ?)
				""", userId, campaignId, prizeId, now, now);
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

	private long userIdByEmail(String email) {
		Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", Long.class, email);
		return id == null ? 0 : id;
	}
}
