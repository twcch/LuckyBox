package com.luckybox.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class WalletApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void createsMockPaymentAndCreditsWalletOnce() throws Exception {
		MockHttpSession session = registerUser();

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointExpiryDays").value(365))
				.andExpect(jsonPath("$.wallet.bonusPointExpiryLabel").value("紅利點自入帳日起 365 天有效。"))
				.andExpect(jsonPath("$.topUpPlans.length()").value(3));

		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"value"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value(500))
				.andExpect(jsonPath("$.pointAmount").value(500))
				.andExpect(jsonPath("$.bonusPointAmount").value(50))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();

		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(500))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(50))
				.andExpect(jsonPath("$.ledger.length()").value(2))
				.andExpect(jsonPath("$.ledger[0].pointKind").value("BONUS"))
				.andExpect(jsonPath("$.ledger[1].pointKind").value("CASH"));

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(500))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(50))
				.andExpect(jsonPath("$.ledger.length()").value(2));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cashPointBalance").value(500))
				.andExpect(jsonPath("$.bonusPointBalance").value(50));
	}

	@Test
	void mockCheckoutConfirmTriggersWebhookAndCreditsWalletOnce() throws Exception {
		MockHttpSession session = registerUser();

		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"collector"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();

		String body = createResult.getResponse().getContentAsString();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).intValue();
		String merchantTradeNo = com.jayway.jsonpath.JsonPath.read(body, "$.merchantTradeNo");

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/mock-checkout/confirm", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.amount").value(1000))
				.andExpect(jsonPath("$.pointAmount").value(1000))
				.andExpect(jsonPath("$.bonusPointAmount").value(150));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(1000))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(150))
				.andExpect(jsonPath("$.ledger.length()").value(2));

		Integer eventCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM payment_webhook_events
				WHERE provider = 'MOCK' AND merchant_trade_no = ?
				""", Integer.class, merchantTradeNo);
		org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(1);

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/mock-checkout/confirm", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(1000))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(150))
				.andExpect(jsonPath("$.ledger.length()").value(2));

		Integer eventCountAfterRetry = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM payment_webhook_events
				WHERE provider = 'MOCK' AND merchant_trade_no = ?
				""", Integer.class, merchantTradeNo);
		org.assertj.core.api.Assertions.assertThat(eventCountAfterRetry).isEqualTo(1);
	}

	@Test
	void mockCheckoutConfirmDoesNotExposeOtherUsersPaymentOrder() throws Exception {
		MockHttpSession ownerSession = registerUser();
		MockHttpSession otherSession = registerUser();

		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(ownerSession)
						.contentType("application/json")
						.content("""
								{"planId":"starter"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();

		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/mock-checkout/confirm", orderId).session(otherSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FOUND"));
	}

	@Test
	void rejectsUnknownTopUpPlan() throws Exception {
		MockHttpSession session = registerUser();

		mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"missing"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TOP_UP_PLAN_NOT_FOUND"));
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "wallet-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "錢包測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
