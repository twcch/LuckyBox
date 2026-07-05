package com.luckybox.ratelimit;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"luckybox.ratelimit.enabled=true",
		"luckybox.ratelimit.auth-limit=3",
		"luckybox.ratelimit.auth-window-seconds=60",
		"luckybox.ratelimit.draw-limit=2",
		"luckybox.ratelimit.draw-window-seconds=60",
		"luckybox.ratelimit.webhook-limit=2",
		"luckybox.ratelimit.webhook-window-seconds=60"
})
class RateLimitApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM wallet_ledger
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ratelimit-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM user_prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'ratelimit-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_results WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'ratelimit-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM draw_orders WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'ratelimit-test-%')
					OR user_id IN (SELECT id FROM users WHERE email LIKE 'ratelimit-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM kuji_tickets WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'ratelimit-test-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM prizes WHERE campaign_id IN (SELECT id FROM kuji_campaigns WHERE slug LIKE 'ratelimit-test-%')
				""");
		jdbcTemplate.update("DELETE FROM kuji_campaigns WHERE slug LIKE 'ratelimit-test-%'");
		jdbcTemplate.update("""
				DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ratelimit-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM payment_orders WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ratelimit-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'ratelimit-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'ratelimit-%'");
	}

	@Test
	void loginIsRateLimitedPerIp() throws Exception {
		String ip = "203.0.113.10";
		String email = "ratelimit-login-" + UUID.randomUUID() + "@example.com";
		register(email, ip);

		// auth-limit=3：登入桶獨立於註冊桶，前 3 次放行、第 4 次超限。
		for (int attempt = 0; attempt < 3; attempt++) {
			mockMvc.perform(loginRequest(email).with(remoteAddr(ip)))
					.andExpect(status().isOk());
		}
		mockMvc.perform(loginRequest(email).with(remoteAddr(ip)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void forgotPasswordIsRateLimitedPerIp() throws Exception {
		String ip = "203.0.113.30";
		for (int attempt = 0; attempt < 3; attempt++) {
			mockMvc.perform(post("/api/auth/forgot-password")
							.with(remoteAddr(ip))
							.contentType("application/json")
							.content("{\"email\":\"ratelimit-unknown@example.com\"}"))
					.andExpect(status().isAccepted());
		}
		mockMvc.perform(post("/api/auth/forgot-password")
						.with(remoteAddr(ip))
						.contentType("application/json")
						.content("{\"email\":\"ratelimit-unknown@example.com\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void paymentWebhookIsRateLimitedPerIp() throws Exception {
		String ip = "203.0.113.50";
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(paymentWebhookRequest().with(remoteAddr(ip)))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(paymentWebhookRequest().with(remoteAddr(ip)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void authBucketsAreIsolatedPerIp() throws Exception {
		// 用 IP-A 將 forgot-password 桶耗盡（3 放行 + 第 4 次超限）。
		String ipA = "203.0.113.40";
		String ipB = "203.0.113.41";
		for (int attempt = 0; attempt < 3; attempt++) {
			mockMvc.perform(forgotRequest().with(remoteAddr(ipA))).andExpect(status().isAccepted());
		}
		mockMvc.perform(forgotRequest().with(remoteAddr(ipA))).andExpect(status().isTooManyRequests());

		// IP-B 必須有完全獨立、未受影響的桶（證明 key 含來源 IP）。
		for (int attempt = 0; attempt < 3; attempt++) {
			mockMvc.perform(forgotRequest().with(remoteAddr(ipB))).andExpect(status().isAccepted());
		}
		mockMvc.perform(forgotRequest().with(remoteAddr(ipB)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void drawBucketsAreIsolatedPerUser() throws Exception {
		// 同一 IP、不同使用者：抽賞桶以 userId 計數，兩人應各自獨立（IP 相同更能凸顯是 per-user）。
		String slug = seedCampaign(20);
		MockHttpSession a = registerSession("ratelimit-draw-a-" + UUID.randomUUID() + "@example.com", "203.0.113.21");
		topUpCollector(a);
		MockHttpSession b = registerSession("ratelimit-draw-b-" + UUID.randomUUID() + "@example.com", "203.0.113.22");
		topUpCollector(b);

		drawRequest(a, slug).andExpect(status().isCreated());
		drawRequest(a, slug).andExpect(status().isCreated());
		// A 已耗盡，B 仍應有完整的獨立桶。
		drawRequest(b, slug).andExpect(status().isCreated());
		drawRequest(b, slug).andExpect(status().isCreated());
		drawRequest(a, slug)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
		drawRequest(b, slug)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void drawIsRateLimitedPerUser() throws Exception {
		String ip = "203.0.113.20";
		String email = "ratelimit-draw-" + UUID.randomUUID() + "@example.com";
		MockHttpSession session = registerSession(email, ip);
		topUpCollector(session);
		String slug = seedCampaign(10);

		// draw-limit=2：同一使用者前 2 抽放行、第 3 抽超限。
		drawRequest(session, slug).andExpect(status().isCreated());
		drawRequest(session, slug).andExpect(status().isCreated());
		drawRequest(session, slug)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	private org.springframework.test.web.servlet.ResultActions drawRequest(MockHttpSession session, String slug)
			throws Exception {
		return mockMvc.perform(post("/api/account/draw-orders")
				.session(session)
				.with(remoteAddr("203.0.113.20"))
				.contentType("application/json")
				.content("""
						{"campaignSlug":"%s","quantity":1,"idempotencyKey":"%s"}
						""".formatted(slug, UUID.randomUUID())));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String email) {
		return post("/api/auth/login")
				.contentType("application/json")
				.content("""
						{"email":"%s","password":"Password123!"}
						""".formatted(email));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder forgotRequest() {
		return post("/api/auth/forgot-password")
				.contentType("application/json")
				.content("{\"email\":\"ratelimit-unknown@example.com\"}");
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder paymentWebhookRequest() {
		return post("/api/webhooks/payment/mock")
				.contentType("application/json")
				.header("X-LuckyBox-Signature", "invalid")
				.content("""
						{
						  "eventId": "ratelimit-%s",
						  "merchantTradeNo": "MOCK-RATELIMIT",
						  "amount": 100,
						  "status": "PAID",
						  "occurredAt": "2026-06-30T00:00:00Z"
						}
						""".formatted(UUID.randomUUID()));
	}

	private void register(String email, String ip) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.with(remoteAddr(ip))
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!","displayName":"節流測試員"}
								""".formatted(email)))
				.andExpect(status().isCreated());
	}

	private MockHttpSession registerSession(String email, String ip) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.with(remoteAddr(ip))
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!","displayName":"節流測試員"}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void topUpCollector(MockHttpSession session) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/account/payment-orders")
						.session(session)
						.contentType("application/json")
						.content("{\"planId\":\"collector\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		int orderId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();
		mockMvc.perform(post("/api/account/payment-orders/{orderId}/complete", orderId).session(session))
				.andExpect(status().isOk());
	}

	private String seedCampaign(int ticketCount) {
		String slug = "ratelimit-test-" + UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_campaigns (
					slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
					ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
					sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
					last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, NULL, NULL, 'MIXED', NULL, 'LuckyBox Test', 100, ?, ?, 'LIVE',
					?, NULL, '測試出貨', '測試退換貨', 0, NULL, 'SERVER_RANDOM', NULL, NULL, ?, ?)
				""", slug, "節流測試池", "節流測試", "節流測試資料", ticketCount, ticketCount, now, now, now);
		Long campaignId = jdbcTemplate.queryForObject("SELECT id FROM kuji_campaigns WHERE slug = ?", Long.class, slug);
		long resolvedCampaignId = campaignId == null ? 0 : campaignId;
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, 'A', '節流測試賞', '測試獎品', NULL, ?, ?, 1, 0, ?, ?)
				""", resolvedCampaignId, ticketCount, ticketCount, now, now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		long resolvedPrizeId = prizeId == null ? 0 : prizeId;
		for (int index = 1; index <= ticketCount; index++) {
			jdbcTemplate.update("""
					INSERT INTO kuji_tickets (
						campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
					)
					VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
					""", resolvedCampaignId, resolvedPrizeId, slug.toUpperCase() + "-" + String.format("%04d", index), now, now);
		}
		return slug;
	}

	private static RequestPostProcessor remoteAddr(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}
}
