package com.luckybox.admin.user;

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
class AdminMemberCompensationApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'compensation-%')
					OR entity_id IN (SELECT CAST(id AS TEXT) FROM users WHERE email LIKE 'compensation-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM user_notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'compensation-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'compensation-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'compensation-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'compensation-%'");
	}

	@Test
	void adminGrantsCompensationCreditsBonusAndRecordsLedgerAuditNotification() throws Exception {
		String email = "compensation-" + UUID.randomUUID() + "@example.com";
		registerMember(email);
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		mockMvc.perform(post("/api/admin/users/{userId}/compensation", userId)
						.session(admin)
						.contentType("application/json")
						.content("{\"amount\":50,\"reason\":\"出貨延遲補償\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value(50))
				.andExpect(jsonPath("$.bonusBalanceAfter").value(50))
				.andExpect(jsonPath("$.ledgerId").isNumber());

		Integer bonus = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE user_id = ?", Integer.class, userId);
		assertThat(bonus).isEqualTo(50);

		Integer ledgerRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM wallet_ledger
				WHERE user_id = ? AND type = 'COMPENSATION' AND point_kind = 'BONUS' AND amount = 50
				  AND reference_type = 'Compensation' AND reason = '出貨延遲補償'
				""", Integer.class, userId);
		assertThat(ledgerRows).isEqualTo(1);

		Integer auditRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_COMPENSATION_GRANTED' AND entity_type = 'User' AND entity_id = ?
				""", Integer.class, String.valueOf(userId));
		assertThat(auditRows).isEqualTo(1);

		Integer notified = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'COMPENSATION_GRANTED'
				""", Integer.class, userId);
		assertThat(notified).isEqualTo(1);
	}

	@Test
	void rejectsInvalidAmountMissingReasonUnknownUserAndNonAdmin() throws Exception {
		String email = "compensation-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = registerMember(email);
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		mockMvc.perform(post("/api/admin/users/{userId}/compensation", userId)
						.session(admin)
						.contentType("application/json")
						.content("{\"amount\":0,\"reason\":\"零點\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COMPENSATION_AMOUNT"));

		mockMvc.perform(post("/api/admin/users/{userId}/compensation", userId)
						.session(admin)
						.contentType("application/json")
						.content("{\"amount\":50,\"reason\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMPENSATION_REASON_REQUIRED"));

		mockMvc.perform(post("/api/admin/users/{userId}/compensation", 999999999L)
						.session(admin)
						.contentType("application/json")
						.content("{\"amount\":50,\"reason\":\"未知會員\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

		mockMvc.perform(post("/api/admin/users/{userId}/compensation", userId)
						.session(member)
						.contentType("application/json")
						.content("{\"amount\":50,\"reason\":\"自我補償\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	private MockHttpSession registerMember(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"displayName\":\"補償測試\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession loginAdmin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"admin@luckybox.local\",\"password\":\"ChangeMe123!\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long userIdByEmail(String email) {
		Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", Long.class, email);
		return id == null ? 0 : id;
	}
}
