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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "luckybox.promo.first-deposit-bonus=100")
class FirstDepositPromoTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void firstTopUpGrantsFirstDepositBonusOnce() throws Exception {
		MockHttpSession session = registerUser();

		// Eligible before any top-up.
		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstDepositPromo.bonusPoints").value(100))
				.andExpect(jsonPath("$.firstDepositPromo.eligible").value(true));

		// First top-up (starter = 100 cash, 0 plan bonus) → +100 first-deposit bonus.
		completeTopUp(session, "starter");

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(100))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(100))
				.andExpect(jsonPath("$.firstDepositPromo.eligible").value(false));

		// Second top-up grants NO further first-deposit bonus.
		completeTopUp(session, "starter");

		mockMvc.perform(get("/api/account/wallet").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet.cashPointBalance").value(200))
				.andExpect(jsonPath("$.wallet.bonusPointBalance").value(100));
	}

	private void completeTopUp(MockHttpSession session, String planId) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("""
								{"planId":"%s"}
								""".formatted(planId)))
				.andExpect(status().isCreated())
				.andReturn();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "firstdep-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "首儲測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
