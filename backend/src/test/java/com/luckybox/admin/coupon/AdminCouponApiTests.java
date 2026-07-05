package com.luckybox.admin.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class AdminCouponApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminCouponTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE entity_type = 'Coupon'
					AND entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM coupons
						WHERE code LIKE 'COUPONTEST%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'admin-coupon-user-%')
					OR entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-coupon-user-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM coupon_usages
				WHERE coupon_id IN (SELECT id FROM coupons WHERE code LIKE 'COUPONTEST%')
				""");
		jdbcTemplate.update("DELETE FROM coupons WHERE code LIKE 'COUPONTEST%'");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-coupon-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-coupon-user-%'");
	}

	@Test
	void adminCreatesListsUpdatesAndPublishesCouponToAccountList() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		MockHttpSession userSession = registerUser();
		String code = "COUPONTEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

		MvcResult createResult = mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson(code, "POINT_BONUS", 120, 500, 5, "ACTIVE")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.type").value("POINT_BONUS"))
				.andExpect(jsonPath("$.typeLabel").value("贈點券"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.statusLabel").value("啟用"))
				.andReturn();
		long couponId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get("/api/admin/coupons")
						.session(adminSession)
						.param("status", "ACTIVE")
						.param("type", "POINT_BONUS")
						.param("q", code))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) couponId))
				.andExpect(jsonPath("$[0].usageLimit").value(5));

		MvcResult accountListResult = mockMvc.perform(get("/api/account/coupons")
						.session(userSession))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(accountListResult.getResponse().getContentAsString()).contains(code);

		mockMvc.perform(patch("/api/admin/coupons/{couponId}", couponId)
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson(code, "POINT_BONUS", 120, 500, 5, "ARCHIVED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) couponId))
				.andExpect(jsonPath("$.status").value("ARCHIVED"));

		MvcResult archivedAccountListResult = mockMvc.perform(get("/api/account/coupons")
						.session(userSession))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(archivedAccountListResult.getResponse().getContentAsString()).doesNotContain(code);

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE entity_type = 'Coupon'
					AND entity_id = ?
					AND action IN ('ADMIN_COUPON_CREATED', 'ADMIN_COUPON_UPDATED')
				""", Integer.class, String.valueOf(couponId));
		assertThat(auditCount).isEqualTo(2);
	}

	@Test
	void normalUserCannotUseAdminCouponApi() throws Exception {
		MockHttpSession userSession = registerUser();
		mockMvc.perform(get("/api/admin/coupons").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void rejectsInvalidCouponPayloadAndDuplicateCode() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String code = "COUPONTEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson(code, "DISCOUNT", 100, 300, 10, "ACTIVE")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson(code, "DISCOUNT", 100, 300, 10, "ACTIVE")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COUPON_CODE_EXISTS"));

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson("BAD CODE", "DISCOUNT", 100, 300, 10, "ACTIVE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COUPON_CODE"));

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson(code + "X", "BOGO", 100, 300, 10, "ACTIVE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COUPON_TYPE"));

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJson(code + "Y", "DISCOUNT", 0, 300, 10, "ACTIVE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COUPON_VALUE"));

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJsonWithPeriod(
								code + "Z",
								"DISCOUNT",
								100,
								300,
								10,
								"2026-07-01T00:00:00Z",
								"2026-06-01T00:00:00Z",
								"ACTIVE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COUPON_PERIOD"));
	}

	@Test
	void adminCreatesVipRestrictedCouponAndRejectsUnknownVipTier() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		String code = "COUPONTEST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

		MvcResult createResult = mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJsonWithVipTier(code, "FREE_SHIPPING", 0, 0, 20, "ACTIVE", "silver")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.vipTier").value("SILVER"))
				.andExpect(jsonPath("$.vipTierLabel").value("銀卡以上"))
				.andReturn();
		long couponId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).longValue();

		mockMvc.perform(get("/api/admin/coupons")
						.session(adminSession)
						.param("q", code))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) couponId))
				.andExpect(jsonPath("$[0].vipTier").value("SILVER"));

		mockMvc.perform(post("/api/admin/coupons")
						.session(adminSession)
						.contentType("application/json")
						.content(couponJsonWithVipTier(
								code + "X",
								"FREE_SHIPPING",
								0,
								0,
								20,
								"ACTIVE",
								"DIAMOND")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COUPON_VIP_TIER"));
	}

	private MockHttpSession loginAdmin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"admin@luckybox.local","password":"ChangeMe123!"}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "admin-coupon-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "優惠券一般會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static String couponJson(String code, String type, int value, int minSpend, int usageLimit, String status) {
		return couponJsonWithPeriod(
				code,
				type,
				value,
				minSpend,
				usageLimit,
				"2026-06-01T00:00:00Z",
				"2026-12-31T23:59:59Z",
				status);
	}

	private static String couponJsonWithPeriod(
			String code,
			String type,
			int value,
			int minSpend,
			int usageLimit,
			String startsAt,
			String endsAt,
			String status) {
		return """
				{
				  "code": "%s",
				  "type": "%s",
				  "value": %d,
				  "minSpend": %d,
				  "usageLimit": %d,
				  "startsAt": "%s",
				  "endsAt": "%s",
				  "status": "%s"
				}
				""".formatted(code, type, value, minSpend, usageLimit, startsAt, endsAt, status);
	}

	private static String couponJsonWithVipTier(
			String code,
			String type,
			int value,
			int minSpend,
			int usageLimit,
			String status,
			String vipTier) {
		return """
				{
				  "code": "%s",
				  "type": "%s",
				  "vipTier": "%s",
				  "value": %d,
				  "minSpend": %d,
				  "usageLimit": %d,
				  "startsAt": "2026-06-01T00:00:00Z",
				  "endsAt": "2026-12-31T23:59:59Z",
				  "status": "%s"
				}
				""".formatted(code, type, vipTier, value, minSpend, usageLimit, status);
	}
}
