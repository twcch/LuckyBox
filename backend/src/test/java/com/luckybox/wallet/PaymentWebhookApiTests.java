package com.luckybox.wallet;

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
class PaymentWebhookApiTests {

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
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'webhook-%')
				)
				""");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'webhook-%')");
		jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'webhook-%')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'webhook-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'webhook-%'");
	}

	@Test
	void paidWebhookCreditsWalletOnceAndDuplicateIsIdempotent() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "value");
		String eventId = "evt-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, order.merchantTradeNo(), 500, "PAID"))
						.content(webhookJson(eventId, order.merchantTradeNo(), 500, "PAID")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.provider").value("MOCK"))
				.andExpect(jsonPath("$.processed").value(true))
				.andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.orderStatus").value("PAID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(500))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(50))
				.andExpect(jsonPath("$.ledger.length()").value(2));

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, order.merchantTradeNo(), 500, "PAID"))
						.content(webhookJson(eventId, order.merchantTradeNo(), 500, "PAID")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.processed").value(true))
				.andExpect(jsonPath("$.duplicate").value(true))
				.andExpect(jsonPath("$.orderStatus").value("PAID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(500))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(50))
				.andExpect(jsonPath("$.ledger.length()").value(2));
	}

	@Test
	void amountMismatchIsRecordedButDoesNotCreditWallet() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "value");
		String eventId = "evt-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, order.merchantTradeNo(), 499, "PAID"))
						.content(webhookJson(eventId, order.merchantTradeNo(), 499, "PAID")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.processed").value(false))
				.andExpect(jsonPath("$.message").value("AMOUNT_MISMATCH"))
				.andExpect(jsonPath("$.orderStatus").value("PENDING"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.ledger.length()").value(0));
	}

	@Test
	void failedWebhookMarksPendingPaymentFailed() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");
		String eventId = "evt-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, order.merchantTradeNo(), 100, "FAILED"))
						.content(webhookJson(eventId, order.merchantTradeNo(), 100, "FAILED")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.processed").value(true))
				.andExpect(jsonPath("$.orderStatus").value("FAILED"));

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", order.id()).session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_PAYABLE"));
	}

	@Test
	void canceledWebhookMarksPendingPaymentCanceled() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");
		String eventId = "evt-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", signature(eventId, order.merchantTradeNo(), 100, "CANCELED"))
						.content(webhookJson(eventId, order.merchantTradeNo(), 100, "CANCELED")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.processed").value(true))
				.andExpect(jsonPath("$.orderStatus").value("CANCELED"));

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", order.id()).session(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_PAYABLE"));
	}

	@Test
	void invalidSignatureIsRejectedBeforeRecordingEvent() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");
		String eventId = "evt-" + UUID.randomUUID();

		mockMvc.perform(post("/api/webhooks/payment/mock")
						.contentType("application/json")
						.header("X-LuckyBox-Signature", "bad-signature")
						.content(webhookJson(eventId, order.merchantTradeNo(), 100, "PAID")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0));
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "webhook-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Webhook 測試玩家"
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
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new PaymentOrderSeed(
				((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue(),
				com.jayway.jsonpath.JsonPath.read(body, "$.merchantTradeNo"));
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
				  "occurredAt": "2026-06-30T00:00:00Z"
				}
				""".formatted(eventId, merchantTradeNo, amount, status);
	}

	private record PaymentOrderSeed(long id, String merchantTradeNo) {
	}
}
