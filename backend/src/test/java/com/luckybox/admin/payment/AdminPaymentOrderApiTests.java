package com.luckybox.admin.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
class AdminPaymentOrderApiTests {

	private static final String MOCK_SECRET = "dev-mock-webhook-secret";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminPaymentOrderTestData() {
		jdbcTemplate.update("""
				DELETE FROM payment_webhook_events
				WHERE merchant_trade_no IN (
					SELECT merchant_trade_no
					FROM payment_orders
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-payment-user-%')
				)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'PaymentOrder'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM payment_orders
						WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-payment-user-%')
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'User'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-payment-user-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-payment-user-%')
					OR (
						reference_type = 'PaymentOrder'
						AND reference_id IN (
							SELECT id
							FROM payment_orders
							WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-payment-user-%')
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-payment-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-payment-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-payment-user-%'");
	}

	@Test
	void adminListsPaymentOrdersWithMaskedUserEmailAndFilters() throws Exception {
		RegisteredUser user = registerUser();
		long pendingOrderId = createPaymentOrder(user.session(), "starter");
		long paidOrderId = createPaymentOrder(user.session(), "value");

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", paidOrderId).session(user.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		MvcResult listResult = mockMvc.perform(get("/api/admin/payment-orders")
						.session(loginAdmin())
						.param("status", "PAID")
						.param("provider", "MOCK")
						.param("q", user.email()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value((int) paidOrderId))
				.andExpect(jsonPath("$[0].userId").value((int) user.id()))
				.andExpect(jsonPath("$[0].userDisplayName").value("後台付款訂單玩家"))
				.andExpect(jsonPath("$[0].maskedUserEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$[0].provider").value("MOCK"))
				.andExpect(jsonPath("$[0].amount").value(500))
				.andExpect(jsonPath("$[0].pointAmount").value(500))
				.andExpect(jsonPath("$[0].bonusPointAmount").value(50))
				.andExpect(jsonPath("$[0].totalPointAmount").value(550))
				.andExpect(jsonPath("$[0].status").value("PAID"))
				.andExpect(jsonPath("$[0].statusLabel").value("已付款"))
				.andExpect(jsonPath("$[0].paidAt").exists())
				.andReturn();

		assertThat(listResult.getResponse().getContentAsString()).doesNotContain(user.email());

		mockMvc.perform(get("/api/admin/payment-orders")
						.session(loginAdmin())
						.param("status", "PENDING")
						.param("q", String.valueOf(pendingOrderId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) pendingOrderId))
				.andExpect(jsonPath("$[0].statusLabel").value("待付款"));
	}

	@Test
	void adminCanReadPaymentOrderProviderPayloadAndWebhookEvents() throws Exception {
		RegisteredUser user = registerUser();
		long orderId = createPaymentOrder(user.session(), "value");
		String merchantTradeNo = merchantTradeNo(orderId);
		String eventId = "evt-admin-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, merchantTradeNo, 500, "PAID"))
						.content(webhookJson(eventId, merchantTradeNo, 500, "PAID")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.orderStatus").value("PAID"));

		MvcResult detailResult = mockMvc.perform(get("/api/admin/payment-orders/{orderId}", orderId)
						.session(loginAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.order.id").value((int) orderId))
				.andExpect(jsonPath("$.order.status").value("PAID"))
				.andExpect(jsonPath("$.providerPayload").exists())
				.andExpect(jsonPath("$.webhookEvents.length()").value(1))
				.andExpect(jsonPath("$.webhookEvents[0].eventId").value(eventId))
				.andExpect(jsonPath("$.webhookEvents[0].status").value("PAID"))
				.andExpect(jsonPath("$.webhookEvents[0].amount").value(500))
				.andExpect(jsonPath("$.webhookEvents[0].processed").value(true))
				.andExpect(jsonPath("$.webhookEvents[0].message").value("OK"))
				.andExpect(jsonPath("$.webhookEvents[0].rawPayload").exists())
				.andReturn();

		assertThat(detailResult.getResponse().getContentAsString())
				.contains(eventId)
				.contains(merchantTradeNo)
				.doesNotContain(user.email());
	}

	@Test
	void normalUserCannotReadAdminPaymentOrders() throws Exception {
		MockHttpSession userSession = registerUser().session();
		mockMvc.perform(get("/api/admin/payment-orders").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void normalUserCannotReadAdminPaymentOrderDetail() throws Exception {
		RegisteredUser user = registerUser();
		long orderId = createPaymentOrder(user.session(), "starter");
		mockMvc.perform(get("/api/admin/payment-orders/{orderId}", orderId).session(user.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsInvalidPaymentOrderStatusFilter() throws Exception {
		mockMvc.perform(get("/api/admin/payment-orders")
						.session(loginAdmin())
						.param("status", "DONE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PAYMENT_ORDER_STATUS"));
	}

	private RegisteredUser registerUser() throws Exception {
		String email = "admin-payment-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台付款訂單玩家"
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

	private String merchantTradeNo(long orderId) {
		return jdbcTemplate.queryForObject(
				"SELECT merchant_trade_no FROM payment_orders WHERE id = ?",
				String.class,
				orderId);
	}

	private static String signature(String eventId, String merchantTradeNo, int amount, String status) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(MOCK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String payload = eventId + "|" + merchantTradeNo + "|" + amount + "|" + status;
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to sign webhook test payload", exception);
		}
	}

	private static String webhookJson(String eventId, String merchantTradeNo, int amount, String status) {
		return """
				{
				  "eventId": "%s",
				  "merchantTradeNo": "%s",
				  "amount": %d,
				  "status": "%s",
				  "occurredAt": "2026-06-30T00:00:00Z"
				}
				""".formatted(eventId, merchantTradeNo, amount, status);
	}

	private record RegisteredUser(long id, String email, MockHttpSession session) {
	}
}
