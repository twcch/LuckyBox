package com.luckybox.wallet;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"luckybox.payment.provider=ECPAY",
		"luckybox.payment.ecpay.enabled=true",
		"luckybox.payment.ecpay.merchant-id=2000132",
		"luckybox.payment.ecpay.hash-key=5294y06JbISpM5x9",
		"luckybox.payment.ecpay.hash-iv=v77hoKGq4kWxNNIS",
		"luckybox.payment.ecpay.action-url=https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5",
		"luckybox.payment.ecpay.return-url=https://example.com/api/webhooks/payment/ecpay",
		"luckybox.payment.ecpay.client-back-url=https://example.com/account/orders",
		"luckybox.payment.ecpay.choose-payment=Credit",
		"luckybox.payment.ecpay.credit-installment=3,6",
		"luckybox.payment.ecpay.accept-simulated=false"
})
class EcpayInstallmentPaymentApiTests {

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
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-installment-%')
				)
				""");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-installment-%')");
		jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-installment-%')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-installment-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'ecpay-installment-%'");
	}

	@Test
	void ecpayCheckoutIncludesCreditInstallmentWhenConfigured() throws Exception {
		MockHttpSession session = registerUser();
		long orderId = createPaymentOrder(session);

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/ecpay-checkout", orderId).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("ECPAY"))
				.andExpect(jsonPath("$.fields.ChoosePayment").value("Credit"))
				.andExpect(jsonPath("$.fields.CreditInstallment").value("3,6"))
				.andExpect(jsonPath("$.fields.CheckMacValue").isNotEmpty());
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "ecpay-installment-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "ECPay 分期測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long createPaymentOrder(MockHttpSession session) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"planId\":\"collector\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.provider").value("ECPAY"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}
}
