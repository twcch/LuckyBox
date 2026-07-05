package com.luckybox.admin.walletledger;

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
class AdminWalletLedgerApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminWalletLedgerTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'PaymentOrder'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM payment_orders
						WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-ledger-user-%')
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'User'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-ledger-user-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-ledger-user-%')
					OR (
						reference_type = 'PaymentOrder'
						AND reference_id IN (
							SELECT id
							FROM payment_orders
							WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-ledger-user-%')
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-ledger-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-ledger-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-ledger-user-%'");
	}

	@Test
	void adminListsWalletLedgerWithMaskedUserEmailAndFilters() throws Exception {
		RegisteredUser user = registerUser();
		long orderId = createPaymentOrder(user.session(), "value");
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		MvcResult bonusResult = mockMvc.perform(get("/api/admin/wallet-ledger")
						.session(loginAdmin())
						.param("type", "TOP_UP_BONUS")
						.param("pointKind", "BONUS")
						.param("referenceType", "PaymentOrder")
						.param("q", user.email()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].userId").value((int) user.id()))
				.andExpect(jsonPath("$[0].userDisplayName").value("後台點數流水玩家"))
				.andExpect(jsonPath("$[0].maskedUserEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$[0].type").value("TOP_UP_BONUS"))
				.andExpect(jsonPath("$[0].typeLabel").value("儲值贈點"))
				.andExpect(jsonPath("$[0].amount").value(50))
				.andExpect(jsonPath("$[0].pointKind").value("BONUS"))
				.andExpect(jsonPath("$[0].pointKindLabel").value("紅利點"))
				.andExpect(jsonPath("$[0].balanceAfter").value(50))
				.andExpect(jsonPath("$[0].referenceType").value("PaymentOrder"))
				.andExpect(jsonPath("$[0].referenceId").value((int) orderId))
				.andExpect(jsonPath("$[0].reason").value("付款儲值贈點"))
				.andExpect(jsonPath("$[0].createdByUserId").value((int) user.id()))
				.andExpect(jsonPath("$[0].createdByDisplayName").value("後台點數流水玩家"))
				.andReturn();

		assertThat(bonusResult.getResponse().getContentAsString()).doesNotContain(user.email());

		mockMvc.perform(get("/api/admin/wallet-ledger")
						.session(loginAdmin())
						.param("type", "TOP_UP")
						.param("pointKind", "CASH")
						.param("q", user.email()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].typeLabel").value("現金儲值"))
				.andExpect(jsonPath("$[0].amount").value(500))
				.andExpect(jsonPath("$[0].balanceAfter").value(500));
	}

	@Test
	void normalUserCannotReadAdminWalletLedger() throws Exception {
		MockHttpSession userSession = registerUser().session();
		mockMvc.perform(get("/api/admin/wallet-ledger").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsInvalidWalletLedgerFilters() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		mockMvc.perform(get("/api/admin/wallet-ledger")
						.session(adminSession)
						.param("type", "UNKNOWN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_WALLET_LEDGER_TYPE"));

		mockMvc.perform(get("/api/admin/wallet-ledger")
						.session(adminSession)
						.param("pointKind", "TOKEN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_POINT_KIND"));
	}

	private RegisteredUser registerUser() throws Exception {
		String email = "admin-ledger-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台點數流水玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new RegisteredUser(userId, email, (MockHttpSession) result.getRequest().getSession(false));
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

	private long createPaymentOrder(MockHttpSession session, String planId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"%s"}
								""".formatted(planId)))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	private record RegisteredUser(long id, String email, MockHttpSession session) {
	}
}
