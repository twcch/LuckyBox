package com.luckybox.account;

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
class Admin2faApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TotpService totpService;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'twofa-%')
					OR entity_id IN (SELECT CAST(id AS TEXT) FROM users WHERE email LIKE 'twofa-%')
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'twofa-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'twofa-%'");
	}

	@Test
	void adminCanEnableTwoFactorAndItIsRequiredAtLogin() throws Exception {
		String email = "twofa-" + UUID.randomUUID() + "@example.com";
		registerAndPromoteToAdmin(email);
		MockHttpSession admin = login(email, null);

		mockMvc.perform(get("/api/admin/2fa").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));

			MvcResult setupResult = mockMvc.perform(post("/api/admin/2fa/setup").session(admin))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.secret").isNotEmpty())
					.andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
					.andExpect(jsonPath("$.qrCodeDataUri").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
					.andReturn();
		String secret = com.jayway.jsonpath.JsonPath
				.read(setupResult.getResponse().getContentAsString(), "$.secret");

		// 啟用前需通過驗證碼。
		mockMvc.perform(post("/api/admin/2fa/enable").session(admin)
						.contentType("application/json")
						.content("{\"code\":\"" + totpService.currentCode(secret) + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true));

		// 啟用後，登入未帶驗證碼 → 401 TWO_FACTOR_REQUIRED。
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TWO_FACTOR_REQUIRED"));

		// 帶錯誤格式驗證碼 → 401 TWO_FACTOR_INVALID。
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"totpCode\":\"12345\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TWO_FACTOR_INVALID"));

		// 帶正確驗證碼 → 200 登入成功。
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"totpCode\":\""
								+ totpService.currentCode(secret) + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void adminCanDisableTwoFactorThenLoginWithoutCode() throws Exception {
		String email = "twofa-" + UUID.randomUUID() + "@example.com";
		registerAndPromoteToAdmin(email);
		MockHttpSession admin = login(email, null);

		MvcResult setupResult = mockMvc.perform(post("/api/admin/2fa/setup").session(admin))
				.andExpect(status().isOk())
				.andReturn();
		String secret = com.jayway.jsonpath.JsonPath
				.read(setupResult.getResponse().getContentAsString(), "$.secret");
		mockMvc.perform(post("/api/admin/2fa/enable").session(admin)
						.contentType("application/json")
						.content("{\"code\":\"" + totpService.currentCode(secret) + "\"}"))
				.andExpect(status().isOk());

		// 停用需通過驗證碼。
		mockMvc.perform(post("/api/admin/2fa/disable").session(admin)
						.contentType("application/json")
						.content("{\"code\":\"" + totpService.currentCode(secret) + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));

		// 停用後登入不再需要驗證碼。
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void normalUserCannotAccessAdminTwoFactor() throws Exception {
		String email = "twofa-" + UUID.randomUUID() + "@example.com";
		MockHttpSession user = register(email);
		mockMvc.perform(get("/api/admin/2fa").session(user))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	private MockHttpSession register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"displayName\":\"二階段測試\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void registerAndPromoteToAdmin(String email) throws Exception {
		register(email);
		jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE lower(email) = lower(?)", email);
	}

	private MockHttpSession login(String email, String totpCode) throws Exception {
		String body = totpCode == null
				? "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"
				: "{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"totpCode\":\"" + totpCode + "\"}";
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content(body))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
