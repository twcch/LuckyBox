package com.luckybox.admin.walletledger;

import static org.assertj.core.api.Assertions.assertThat;
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
class AdminWalletAdjustmentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'Wallet'
					AND entity_id IN (
						SELECT CAST(id AS TEXT) FROM users WHERE email LIKE 'adjust-user-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'adjust-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'adjust-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'adjust-user-%'");
	}

	@Test
	void adminCanGrantAndDeductPointsWithReason() throws Exception {
		String email = "adjust-user-" + UUID.randomUUID() + "@example.com";
		registerMember(email);
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		// 加贈點 +100。
		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(userId, "BONUS", 100, "客服補償-活動延遲")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("ADJUSTMENT"))
				.andExpect(jsonPath("$.typeLabel").value("人工調整"))
				.andExpect(jsonPath("$.amount").value(100))
				.andExpect(jsonPath("$.pointKind").value("BONUS"))
				.andExpect(jsonPath("$.balanceAfter").value(100))
				.andExpect(jsonPath("$.reason").value("客服補償-活動延遲"));

		// 加現金點 +50，再扣 30 → 餘 20。
		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(userId, "CASH", 50, "測試加點")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.balanceAfter").value(50));
		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(userId, "CASH", -30, "測試扣點")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value(-30))
				.andExpect(jsonPath("$.balanceAfter").value(20));

		Integer cash = jdbcTemplate.queryForObject(
				"SELECT cash_point_balance FROM wallets WHERE user_id = ?", Integer.class, userId);
		Integer bonus = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE user_id = ?", Integer.class, userId);
		assertThat(cash).isEqualTo(20);
		assertThat(bonus).isEqualTo(100);

		Integer adjustmentRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ? AND type = 'ADJUSTMENT'", Integer.class, userId);
		assertThat(adjustmentRows).isEqualTo(3);

		Integer auditRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_WALLET_ADJUSTED' AND entity_type = 'Wallet' AND entity_id = ?
				""", Integer.class, String.valueOf(userId));
		assertThat(auditRows).isEqualTo(3);
	}

	@Test
	void deductionThatWouldGoNegativeIsRejected() throws Exception {
		String email = "adjust-user-" + UUID.randomUUID() + "@example.com";
		registerMember(email);
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(userId, "CASH", -10, "嘗試扣到負數")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WALLET_ADJUSTMENT_INSUFFICIENT"));

		Integer rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ? AND type = 'ADJUSTMENT'", Integer.class, userId);
		assertThat(rows).isEqualTo(0);
	}

	@Test
	void rejectsMissingReasonZeroAmountAndUnknownUser() throws Exception {
		String email = "adjust-user-" + UUID.randomUUID() + "@example.com";
		registerMember(email);
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(userId, "BONUS", 50, "   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WALLET_ADJUSTMENT_REASON_REQUIRED"));

		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(userId, "BONUS", 0, "零點")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_WALLET_ADJUSTMENT"));

		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(admin)
						.contentType("application/json")
						.content(adjustJson(999999999L, "BONUS", 50, "未知會員")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void normalMemberCannotAdjustWallet() throws Exception {
		String email = "adjust-user-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = registerMember(email);
		long userId = userIdByEmail(email);

		mockMvc.perform(post("/api/admin/wallet-adjustments")
						.session(member)
						.contentType("application/json")
						.content(adjustJson(userId, "BONUS", 100, "自我加點")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	private MockHttpSession registerMember(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "點數調整測試會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
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

	private static String adjustJson(long userId, String pointKind, int amount, String reason) {
		return """
				{"userId":%d,"pointKind":"%s","amount":%d,"reason":"%s"}
				""".formatted(userId, pointKind, amount, reason);
	}
}
