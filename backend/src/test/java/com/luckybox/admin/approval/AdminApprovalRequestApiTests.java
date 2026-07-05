package com.luckybox.admin.approval;

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
class AdminApprovalRequestApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'AdminApprovalRequest'
					OR actor_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
					OR entity_id IN (SELECT CAST(id AS TEXT) FROM users WHERE email LIKE 'approval-%')
					OR (
						entity_type = 'PaymentOrder'
						AND entity_id IN (
							SELECT CAST(id AS TEXT)
							FROM payment_orders
							WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM admin_approval_requests
				WHERE requested_by IN (SELECT id FROM users WHERE email LIKE 'approval-%')
					OR (entity_type = 'User' AND entity_id IN (
						SELECT CAST(id AS TEXT) FROM users WHERE email LIKE 'approval-%'
					))
					OR (entity_type = 'PaymentOrder' AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM payment_orders
						WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
					))
				""");
		jdbcTemplate.update("""
				DELETE FROM user_notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'approval-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'approval-%'");
	}

	@Test
	void superAdminApprovesWalletAdjustmentRequestIntoLedger() throws Exception {
		RegisteredUser user = registerUser("審核點數會員");
		MockHttpSession superAdmin = loginAdmin();

		MvcResult createResult = mockMvc.perform(post("/api/admin/approval-requests/wallet-adjustments")
						.session(superAdmin)
						.contentType("application/json")
						.content("""
								{"userId":%d,"pointKind":"BONUS","amount":120,"reason":"活動補點需複核"}
								""".formatted(user.id())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("WALLET_ADJUSTMENT"))
				.andExpect(jsonPath("$.typeLabel").value("點數調整"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.statusLabel").value("待審核"))
				.andReturn();
		long requestId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get("/api/admin/approval-requests")
						.session(superAdmin)
						.param("status", "PENDING")
						.param("type", "WALLET_ADJUSTMENT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) requestId));

		mockMvc.perform(post("/api/admin/approval-requests/{requestId}/approve", requestId).session(superAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.resultEntityType").value("WalletLedger"))
				.andExpect(jsonPath("$.resultEntityId").isString());

		Integer bonus = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE user_id = ?", Integer.class, user.id());
		assertThat(bonus).isEqualTo(120);

		Integer ledgerRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM wallet_ledger
				WHERE user_id = ? AND type = 'ADJUSTMENT' AND amount = 120
				""", Integer.class, user.id());
		assertThat(ledgerRows).isEqualTo(1);

		mockMvc.perform(post("/api/admin/approval-requests/{requestId}/approve", requestId).session(superAdmin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("APPROVAL_NOT_PENDING"));
	}

	@Test
	void superAdminApprovesPaymentRefundRequest() throws Exception {
		RegisteredUser user = registerUser("審核退款會員");
		long orderId = topUpCollector(user.session());
		MockHttpSession superAdmin = loginAdmin();

		MvcResult createResult = mockMvc.perform(post("/api/admin/approval-requests/payment-refunds/{orderId}", orderId)
						.session(superAdmin)
						.contentType("application/json")
						.content("{\"reason\":\"重複儲值需退款\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("PAYMENT_REFUND"))
				.andExpect(jsonPath("$.entityType").value("PaymentOrder"))
				.andExpect(jsonPath("$.entityId").value(String.valueOf(orderId)))
				.andReturn();
		long requestId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(post("/api/admin/approval-requests/{requestId}/approve", requestId).session(superAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.resultEntityType").value("PaymentOrder"))
				.andExpect(jsonPath("$.resultEntityId").value(String.valueOf(orderId)));

		String orderStatus = jdbcTemplate.queryForObject(
				"SELECT status FROM payment_orders WHERE id = ?", String.class, orderId);
		assertThat(orderStatus).isEqualTo("REFUNDED");
	}

	@Test
	void compensationRequestCanBeRejectedAndOnlySuperAdminCanReview() throws Exception {
		RegisteredUser user = registerUser("審核補償會員");
		RegisteredUser normalAdmin = registerUser("一般管理員");
		jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", normalAdmin.id());
		MockHttpSession adminSession = login(normalAdmin.email(), "Password123!");
		MockHttpSession superAdmin = loginAdmin();

		MvcResult createResult = mockMvc.perform(post("/api/admin/approval-requests/compensations/{userId}", user.id())
						.session(superAdmin)
						.contentType("application/json")
						.content("{\"amount\":80,\"reason\":\"出貨延遲補償需複核\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("COMPENSATION"))
				.andReturn();
		long requestId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(post("/api/admin/approval-requests/{requestId}/approve", requestId).session(adminSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SUPER_ADMIN_REQUIRED"));

		mockMvc.perform(post("/api/admin/approval-requests/{requestId}/reject", requestId)
						.session(superAdmin)
						.contentType("application/json")
						.content("{\"reason\":\"補償資料不足\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		Integer ledgerRows = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM wallet_ledger
				WHERE user_id = ? AND type = 'COMPENSATION'
				""", Integer.class, user.id());
		assertThat(ledgerRows).isZero();
	}

	private RegisteredUser registerUser(String displayName) throws Exception {
		String email = "approval-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!","displayName":"%s"}
								""".formatted(email, displayName)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new RegisteredUser(userId, email, (MockHttpSession) result.getRequest().getSession(false));
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
		return login("admin@luckybox.local", "ChangeMe123!");
	}

	private MockHttpSession login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private record RegisteredUser(long id, String email, MockHttpSession session) {
	}
}
