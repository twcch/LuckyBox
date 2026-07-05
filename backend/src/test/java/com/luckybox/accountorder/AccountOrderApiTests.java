package com.luckybox.accountorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
class AccountOrderApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAccountOrderTestData() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'account-order-user-%')
					OR (
						reference_type = 'DrawOrder'
						AND reference_id IN (
							SELECT d.id
							FROM draw_orders d
							JOIN kuji_campaigns c ON c.id = d.campaign_id
							WHERE c.slug LIKE 'account-order-test-%'
						)
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'account-order-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'account-order-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'account-order-test-%')
					OR user_id IN (SELECT id FROM users WHERE email LIKE 'account-order-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'account-order-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes
				WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'account-order-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'account-order-test-%'");
		jdbcTemplate.update("""
				DELETE FROM payment_orders
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'account-order-user-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'account-order-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'account-order-user-%'");
	}

	@Test
	void memberListsOwnPaymentAndDrawOrdersOnly() throws Exception {
		MockHttpSession firstUser = registerUser("第一位玩家");
		topUp(firstUser, "collector");
		String firstSlug = seedCampaign("account-order-test-a-", 4);
		mockMvc.perform(post("/api/account/draw-orders")
						.session(firstUser)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":2,"idempotencyKey":"%s"}
								""".formatted(firstSlug, UUID.randomUUID())))
				.andExpect(status().isCreated());

		MockHttpSession secondUser = registerUser("第二位玩家");
		topUp(secondUser, "starter");
		String secondSlug = seedCampaign("account-order-test-b-", 2);
		mockMvc.perform(post("/api/account/draw-orders")
						.session(secondUser)
						.contentType("application/json")
						.content("""
								{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
								""".formatted(secondSlug, UUID.randomUUID())))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(get("/api/account/orders").session(firstUser))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.drawOrders.length()").value(1))
				.andExpect(jsonPath("$.drawOrders[0].campaignSlug").value(firstSlug))
				.andExpect(jsonPath("$.drawOrders[0].quantity").value(2))
				.andExpect(jsonPath("$.drawOrders[0].pointSpent").value(200))
				.andExpect(jsonPath("$.drawOrders[0].statusLabel").value("完成"))
				.andExpect(jsonPath("$.drawOrders[0].resultCount").value(2))
				.andExpect(jsonPath("$.drawOrders[0].results.length()").value(2))
				.andExpect(jsonPath("$.drawOrders[0].prizeSummary").value("A賞 會員訂單測試賞、A賞 會員訂單測試賞"))
				.andExpect(jsonPath("$.paymentOrders.length()").value(1))
				.andExpect(jsonPath("$.paymentOrders[0].status").value("PAID"))
				.andExpect(jsonPath("$.paymentOrders[0].statusLabel").value("已付款"))
				.andExpect(jsonPath("$.paymentOrders[0].pointAmount").value(1000))
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain(secondSlug);
	}

	@Test
	void rejectsAnonymousAccountOrderRead() throws Exception {
		mockMvc.perform(get("/api/account/orders"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	private MockHttpSession registerUser(String displayName) throws Exception {
		String email = "account-order-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "%s"
								}
								""".formatted(email, displayName)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void topUp(MockHttpSession session, String planId) throws Exception {
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

	private String seedCampaign(String prefix, int ticketCount) {
		String slug = prefix + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, '會員訂單測試池', '會員訂單', '會員訂單 API 測試資料', NULL, NULL,
					'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE', ?, NULL, '測試出貨',
					'測試退換貨', 0, NULL, 'SERVER_RANDOM', ?, NULL, ?, ?)
				""", slug, ticketCount, ticketCount, now, "test-seed-" + slug, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long prizeId = insertPrize(campaignId == null ? 0 : campaignId, ticketCount, now);
		for (int index = 1; index <= ticketCount; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id,
						drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", campaignId, prizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		return slug;
	}

	private long insertPrize(long campaignId, int ticketCount, String now) {
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '會員訂單測試賞', '會員訂單測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", campaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}
}
