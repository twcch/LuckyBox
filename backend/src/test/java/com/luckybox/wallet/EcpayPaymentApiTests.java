package com.luckybox.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
		"luckybox.payment.ecpay.accept-simulated=false"
})
class EcpayPaymentApiTests {

	private static final String HASH_KEY = "5294y06JbISpM5x9";
	private static final String HASH_IV = "v77hoKGq4kWxNNIS";

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
					WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-%')
				)
				""");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-%')");
		jdbcTemplate.update("DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-%')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ecpay-%')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'ecpay-%'");
	}

	@Test
	void createsEcpayOrderAndCheckoutFields() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "value");

		mockMvc.perform(post("/api/account/payment-orders/{orderId}/ecpay-checkout", order.id()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("ECPAY"))
				.andExpect(jsonPath("$.actionUrl").value("https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5"))
				.andExpect(jsonPath("$.method").value("POST"))
				.andExpect(jsonPath("$.fields.MerchantID").value("2000132"))
				.andExpect(jsonPath("$.fields.MerchantTradeNo").value(order.merchantTradeNo()))
				.andExpect(jsonPath("$.fields.TotalAmount").value("500"))
				.andExpect(jsonPath("$.fields.ChoosePayment").value("Credit"))
				.andExpect(jsonPath("$.fields.EncryptType").value("1"))
				.andExpect(jsonPath("$.fields.CheckMacValue").isNotEmpty());
	}

	@Test
	void paidEcpayCallbackCreditsWalletOnceAndDuplicateIsIdempotent() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "collector");
		Map<String, String> callback = paidCallback(order.merchantTradeNo(), "EC" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), 1000, "0");

		mockMvc.perform(ecpayCallback(callback))
				.andExpect(status().isOk())
				.andExpect(content().string("1|OK"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(1000))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(150))
				.andExpect(jsonPath("$.ledger.length()").value(2));

		mockMvc.perform(ecpayCallback(callback))
				.andExpect(status().isOk())
				.andExpect(content().string("1|OK"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(1000))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(150))
				.andExpect(jsonPath("$.ledger.length()").value(2));
	}

	@Test
	void invalidEcpayChecksumIsRejectedBeforeRecordingEvent() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");
		Map<String, String> callback = paidCallback(order.merchantTradeNo(), "ECBAD" + UUID.randomUUID().toString().substring(0, 8), 100, "0");
		callback.put("CheckMacValue", "BAD");

		mockMvc.perform(ecpayCallback(callback))
				.andExpect(status().isOk())
				.andExpect(content().string("0|WEBHOOK_SIGNATURE_INVALID"));

		Integer eventCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM payment_webhook_events
				WHERE provider = 'ECPAY' AND merchant_trade_no = ?
				""", Integer.class, order.merchantTradeNo());
		assertThat(eventCount).isZero();
	}

	@Test
	void amountMismatchIsRecordedButDoesNotCreditWallet() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "value");
		Map<String, String> callback = paidCallback(order.merchantTradeNo(), "ECMM" + UUID.randomUUID().toString().substring(0, 8), 499, "0");

		mockMvc.perform(ecpayCallback(callback))
				.andExpect(status().isOk())
				.andExpect(content().string("0|AMOUNT_MISMATCH"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.ledger.length()").value(0));
	}

	@Test
	void simulatedPaidCallbackIsRecordedButNotCreditedByDefault() throws Exception {
		MockHttpSession session = registerUser();
		PaymentOrderSeed order = createPaymentOrder(session, "starter");
		Map<String, String> callback = paidCallback(order.merchantTradeNo(), "ECSIM" + UUID.randomUUID().toString().substring(0, 8), 100, "1");

		mockMvc.perform(ecpayCallback(callback))
				.andExpect(status().isOk())
				.andExpect(content().string("1|OK"));

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(0))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(0))
				.andExpect(jsonPath("$.ledger.length()").value(0));

		String message = jdbcTemplate.queryForObject("""
				SELECT message
				FROM payment_webhook_events
				WHERE provider = 'ECPAY' AND merchant_trade_no = ?
				""", String.class, order.merchantTradeNo());
		assertThat(message).isEqualTo("ECPAY_SIMULATED_PAYMENT_IGNORED");
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "ecpay-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "ECPay 測試玩家"
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
				.andExpect(jsonPath("$.provider").value("ECPAY"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new PaymentOrderSeed(
				((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue(),
				com.jayway.jsonpath.JsonPath.read(body, "$.merchantTradeNo"));
	}

	private static org.springframework.test.web.servlet.RequestBuilder ecpayCallback(Map<String, String> fields) {
		return post("/api/webhooks/payment/ecpay")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.content(formBody(fields));
	}

	private static Map<String, String> paidCallback(String merchantTradeNo, String tradeNo, int amount, String simulatePaid) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("MerchantID", "2000132");
		fields.put("MerchantTradeNo", merchantTradeNo);
		fields.put("RtnCode", "1");
		fields.put("RtnMsg", "交易成功");
		fields.put("TradeNo", tradeNo);
		fields.put("TradeAmt", String.valueOf(amount));
		fields.put("PaymentDate", "2026/07/04 12:00:00");
		fields.put("PaymentType", "Credit_CreditCard");
		fields.put("PaymentTypeChargeFee", "0");
		fields.put("TradeDate", "2026/07/04 11:59:00");
		fields.put("SimulatePaid", simulatePaid);
		fields.put("CheckMacValue", EcpayChecksum.generate(fields, HASH_KEY, HASH_IV));
		return fields;
	}

	private static String formBody(Map<String, String> fields) {
		return fields.entrySet().stream()
				.map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
				.collect(Collectors.joining("&"));
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private record PaymentOrderSeed(long id, String merchantTradeNo) {
	}
}
