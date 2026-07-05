package com.luckybox.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
		"luckybox.payment.provider=LINEPAY",
		"luckybox.payment.linepay.enabled=true",
		"luckybox.payment.linepay.channel-id=test-channel",
		"luckybox.payment.linepay.channel-secret=test-secret",
		"luckybox.payment.linepay.api-base-url=https://sandbox-api-pay.line.me",
		"luckybox.app.base-url=https://example.com"
})
class LinePayPaymentApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FakeLinePayClient linePayClient;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM payment_webhook_events
				WHERE merchant_trade_no IN (
					SELECT merchant_trade_no
					FROM payment_orders
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'linepay-%')
				)
				""");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'linepay-%')");
		jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'linepay-%')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'linepay-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'linepay-%'");
		linePayClient.reset();
	}

	@Test
	void linePayCheckoutReturnsRedirectUrlAndConfirmCreditsWalletOnce() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "value");

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/linepay-checkout", order.id()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("LINEPAY"))
				.andExpect(jsonPath("$.merchantTradeNo").value(order.merchantTradeNo()))
				.andExpect(jsonPath("$.redirectUrl").value("https://linepay.example/web/pay"))
				.andExpect(jsonPath("$.appRedirectUrl").value("line://pay/payment/test"))
				.andExpect(jsonPath("$.transactionId").value("202607040001"));

		assertThat(linePayClient.lastRequest.orderId()).isEqualTo(order.merchantTradeNo());
		assertThat(linePayClient.lastRequest.amount()).isEqualTo(500);
		assertThat(linePayClient.lastRequest.confirmUrl())
				.isEqualTo("https://example.com/api/webhooks/payment/linepay/confirm/" + order.merchantTradeNo());

		mockMvc.perform(get("/api/webhooks/payment/linepay/confirm/{merchantTradeNo}", order.merchantTradeNo())
						.param("transactionId", "202607040001"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "https://example.com/account/wallet?payment=success"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(500))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(50))
				.andExpect(jsonPath("$.ledger.length()").value(2));

		mockMvc.perform(get("/api/webhooks/payment/linepay/confirm/{merchantTradeNo}", order.merchantTradeNo())
						.param("transactionId", "202607040001"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(500))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(50))
				.andExpect(jsonPath("$.ledger.length()").value(2));
		assertThat(linePayClient.confirmCalls).isEqualTo(1);
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "linepay-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "LINE Pay 測試玩家"
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
				.andExpect(jsonPath("$.provider").value("LINEPAY"))
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
	static class FakeLinePayClientConfiguration {
		@Bean
		@Primary
		FakeLinePayClient fakeLinePayClient() {
			return new FakeLinePayClient();
		}
	}

	static class FakeLinePayClient implements LinePayClient {
		private LinePayPaymentRequest lastRequest;
		private int confirmCalls;

		@Override
		public LinePayPaymentResult requestPayment(LinePayPaymentRequest request) {
			lastRequest = request;
			return new LinePayPaymentResult(
					"0000",
					"Success.",
					"202607040001",
					"https://linepay.example/web/pay",
					"line://pay/payment/test",
					"{\"returnCode\":\"0000\",\"info\":{\"transactionId\":\"202607040001\"}}");
		}

		@Override
		public LinePayConfirmResult confirmPayment(String transactionId, int amount, String currency) {
			confirmCalls++;
			return new LinePayConfirmResult("0000", "Success.", "{\"returnCode\":\"0000\"}");
		}

		private void reset() {
			lastRequest = null;
			confirmCalls = 0;
		}
	}
}
