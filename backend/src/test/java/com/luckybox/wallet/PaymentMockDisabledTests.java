package com.luckybox.wallet;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "luckybox.payment.mock-enabled=false")
class PaymentMockDisabledTests {

	private static final String MOCK_SECRET = "dev-mock-webhook-secret";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM payment_webhook_events
				WHERE merchant_trade_no IN (
					SELECT merchant_trade_no
					FROM payment_orders
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'mock-disabled-%')
				)
				""");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'mock-disabled-%')");
		jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'mock-disabled-%')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'mock-disabled-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'mock-disabled-%'");
	}

	@Test
	void legacyAndCheckoutMockCompletionAreUnavailableWhenMockPaymentIsDisabled() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", order.id()).session(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAYMENT_MOCK_DISABLED"));

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/mock-checkout/confirm", order.id()).session(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAYMENT_MOCK_DISABLED"));

		assertPaymentOrderStatus(order.id(), "PENDING");
		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.ledger.length()").value(0));
	}

	@Test
	void mockWebhookIsUnavailableAndNotRecordedWhenMockPaymentIsDisabled() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "value");
		String eventId = "evt-disabled-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, order.merchantTradeNo(), 500, "PAID"))
						.content(webhookJson(eventId, order.merchantTradeNo(), 500, "PAID")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAYMENT_MOCK_DISABLED"));

		Integer eventCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM payment_webhook_events
				WHERE provider = 'MOCK' AND event_id = ?
				""", Integer.class, eventId);
		assertThat(eventCount).isZero();
		assertPaymentOrderStatus(order.id(), "PENDING");
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "mock-disabled-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Mock 關閉測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private PaymentOrderSeed createPaymentOrder(MockHttpSession session, String planId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("{\"planId\":\"" + planId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new PaymentOrderSeed(
				((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue(),
				com.jayway.jsonpath.JsonPath.read(body, "$.merchantTradeNo"));
	}

	private void assertPaymentOrderStatus(long orderId, String expectedStatus) {
		String status = jdbcTemplate.queryForObject(
				"SELECT status FROM payment_orders WHERE id = ?",
				String.class,
				orderId);
		assertThat(status).isEqualTo(expectedStatus);
	}

	private static String signature(String eventId, String merchantTradeNo, int amount, String status) {
		return PaymentWebhookSignature.sign(MOCK_SECRET, eventId + "|" + merchantTradeNo + "|" + amount + "|" + status);
	}

	private static String webhookJson(String eventId, String merchantTradeNo, int amount, String status) {
		return """
				{
				  "eventId": "%s",
				  "merchantTradeNo": "%s",
				  "amount": %d,
				  "status": "%s",
				  "occurredAt": "2026-07-01T00:00:00Z"
				}
				""".formatted(eventId, merchantTradeNo, amount, status);
	}

	private record PaymentOrderSeed(long id, String merchantTradeNo) {
	}
}
