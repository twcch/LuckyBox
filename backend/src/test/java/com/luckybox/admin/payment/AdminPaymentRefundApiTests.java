package com.luckybox.admin.payment;

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
class AdminPaymentRefundApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'refund-%')
					OR (entity_type = 'PaymentOrder' AND entity_id IN (
						SELECT CAST(id AS TEXT) FROM payment_orders
						WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'refund-%')
					))
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'refund-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'refund-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'refund-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'refund-%'");
	}

	@Test
	void adminRefundReversesCreditedPointsAndMarksRefunded() throws Exception {
		String email = "refund-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = register(email);
		long orderId = topUpCollector(member);
		long userId = userIdByEmail(email);

		MockHttpSession admin = loginAdmin();
		mockMvc.perform(post("/api/admin/payment-orders/{orderId}/refund", orderId)
						.session(admin)
						.contentType("application/json")
						.content("{\"reason\":\"客戶誤儲值，全額退點\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REFUNDED"))
				.andExpect(jsonPath("$.statusLabel").value("已退款"));

		Integer cash = jdbcTemplate.queryForObject(
				"SELECT cash_point_balance FROM wallets WHERE user_id = ?", Integer.class, userId);
		Integer bonus = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE user_id = ?", Integer.class, userId);
		assertThat(cash).isEqualTo(0);
		assertThat(bonus).isEqualTo(0);

		Integer refundRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ? AND type = 'REFUND'", Integer.class, userId);
		assertThat(refundRows).isEqualTo(2);
		Integer negativeRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ? AND type = 'REFUND' AND amount < 0",
				Integer.class, userId);
		assertThat(negativeRows).isEqualTo(2);

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM audit_logs
				WHERE action = 'ADMIN_PAYMENT_REFUNDED' AND entity_type = 'PaymentOrder' AND entity_id = ?
				""", Integer.class, String.valueOf(orderId));
		assertThat(auditCount).isEqualTo(1);

		// 已退款訂單不可再退款。
		mockMvc.perform(post("/api/admin/payment-orders/{orderId}/refund", orderId)
						.session(admin)
						.contentType("application/json")
						.content("{\"reason\":\"重複退款\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_REFUNDABLE"));
	}

	@Test
	void refundRequiresReasonAndRejectsWhenBalanceInsufficient() throws Exception {
		String email = "refund-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = register(email);
		long orderId = topUpCollector(member);
		long userId = userIdByEmail(email);
		MockHttpSession admin = loginAdmin();

		// 缺原因 → 400。
		mockMvc.perform(post("/api/admin/payment-orders/{orderId}/refund", orderId)
						.session(admin)
						.contentType("application/json")
						.content("{\"reason\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REFUND_REASON_REQUIRED"));

		// 模擬會員已使用點數（現金點降到不足回收）。
		jdbcTemplate.update("UPDATE wallets SET cash_point_balance = 100 WHERE user_id = ?", userId);
		mockMvc.perform(post("/api/admin/payment-orders/{orderId}/refund", orderId)
						.session(admin)
						.contentType("application/json")
						.content("{\"reason\":\"嘗試退款\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REFUND_INSUFFICIENT_BALANCE"));

		// 退款失敗後訂單仍為 PAID、且未寫入 REFUND 流水（交易已回滾）。
		String orderStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM payment_orders WHERE id = ?", String.class, orderId);
		assertThat(orderStatus).isEqualTo("PAID");
		Integer refundRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM wallet_ledger WHERE user_id = ? AND type = 'REFUND'", Integer.class, userId);
		assertThat(refundRows).isEqualTo(0);
	}

	@Test
	void normalUserCannotRefundAndUnknownOrderIsNotFound() throws Exception {
		String email = "refund-" + UUID.randomUUID() + "@example.com";
		MockHttpSession member = register(email);
		long orderId = topUpCollector(member);

		mockMvc.perform(post("/api/admin/payment-orders/{orderId}/refund", orderId)
						.session(member)
						.contentType("application/json")
						.content("{\"reason\":\"我要退款\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		MockHttpSession admin = loginAdmin();
		mockMvc.perform(post("/api/admin/payment-orders/{orderId}/refund", 999999999L)
						.session(admin)
						.contentType("application/json")
						.content("{\"reason\":\"未知訂單\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
	}

	private MockHttpSession register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"displayName\":\"退款測試\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long topUpCollector(MockHttpSession session) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("{\"planId\":\"collector\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		long orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
		return orderId;
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
