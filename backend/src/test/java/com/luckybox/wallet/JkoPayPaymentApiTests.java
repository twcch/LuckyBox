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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"luckybox.payment.provider=JKOPAY",
		"luckybox.payment.jkopay.enabled=true",
		"luckybox.payment.jkopay.api-key=test-api-key",
		"luckybox.payment.jkopay.secret-key=test-secret",
		"luckybox.payment.jkopay.store-id=store-001",
		"luckybox.payment.jkopay.entry-url=https://test-onlinepay.jkopay.app/platform/entry",
		"luckybox.app.base-url=https://example.com"
})
class JkoPayPaymentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FakeJkoPayClient jkoPayClient;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM payment_webhook_events
				WHERE merchant_trade_no IN (
					SELECT merchant_trade_no
					FROM payment_orders
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'jkopay-%')
				)
				""");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'jkopay-%')");
		jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'jkopay-%')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'jkopay-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'jkopay-%'");
		jkoPayClient.reset();
	}

	@Test
	void jkoPayCheckoutConfirmAndResultCallbackCreditWalletOnce() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/jkopay-checkout", order.id()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("JKOPAY"))
				.andExpect(jsonPath("$.merchantTradeNo").value(order.merchantTradeNo()))
				.andExpect(jsonPath("$.redirectUrl").value("https://jkopay.example/payment"))
				.andExpect(jsonPath("$.qrImageUrl").value("https://jkopay.example/qr.png"));

		assertThat(jkoPayClient.lastRequest.platformOrderId()).isEqualTo(order.merchantTradeNo());
		assertThat(jkoPayClient.lastRequest.storeId()).isEqualTo("store-001");
		assertThat(jkoPayClient.lastRequest.resultUrl()).isEqualTo("https://example.com/api/webhooks/payment/jkopay/result");

		mockMvc.perform(post("/api/webhooks/payment/jkopay/confirm")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"platform_order_id\":\"" + order.merchantTradeNo() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true));

		String payload = """
				{
				  "transaction": {
				    "platform_order_id": "%s",
				    "status": 0,
				    "tradeNo": "JKO202607040001",
				    "trans_time": "2026-07-04 12:00:00",
				    "currency": "TWD",
				    "final_price": "100",
				    "redeem_amount": "0",
				    "debit_amount": "100",
				    "channel_type": "account"
				  }
				}
				""".formatted(order.merchantTradeNo());

		mockMvc.perform(post("/api/webhooks/payment/jkopay/result")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.processed").value(true))
				.andExpect(jsonPath("$.duplicate").value(false))
				.andExpect(jsonPath("$.orderStatus").value("PAID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(100))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.ledger.length()").value(1));

		mockMvc.perform(post("/api/webhooks/payment/jkopay/result")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.duplicate").value(true));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(100))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.ledger.length()").value(1));
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "jkopay-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "街口支付測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private PaymentOrderSeed createPaymentOrder(MockHttpSession session, String planId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"planId\":\"" + planId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.provider").value("JKOPAY"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new PaymentOrderSeed(
				((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue(),
				com.jayway.jsonpath.JsonPath.read(body, "$.merchantTradeNo"));
	}

	private record PaymentOrderSeed(long id, String merchantTradeNo) {
	}

	@TestConfiguration
	static class FakeJkoPayClientConfiguration {
		@Bean
		@Primary
		FakeJkoPayClient fakeJkoPayClient() {
			return new FakeJkoPayClient();
		}
	}

	static class FakeJkoPayClient implements JkoPayClient {
		private JkoPayEntryRequest lastRequest;

		@Override
		public JkoPayEntryResult createEntry(JkoPayEntryRequest request) {
			lastRequest = request;
			return new JkoPayEntryResult(
					"000",
					null,
					"https://jkopay.example/payment",
					"https://jkopay.example/qr.png",
					"1528447912",
					"{\"result\":\"000\",\"result_object\":{\"payment_url\":\"https://jkopay.example/payment\"}}");
		}

		private void reset() {
			lastRequest = null;
		}
	}
}
