package com.luckybox.config;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "luckybox.security.csrf-enabled=true")
class CsrfApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("""
				DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'csrf-test-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'csrf-test-%'");
	}

	@Test
	void mutationWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content(registerJson("csrf-test-" + UUID.randomUUID() + "@example.com")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
	}

	@Test
	void safeRequestPrimesXsrfCookieAndTokenLetsMutationThrough() throws Exception {
		// 任一安全請求（GET）應寫出 XSRF-TOKEN cookie。
		MvcResult primed = mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie xsrf = primed.getResponse().getCookie("XSRF-TOKEN");
		assertThat(xsrf).as("GET 應寫出 XSRF-TOKEN cookie").isNotNull();
		assertThat(xsrf.getValue()).isNotBlank();

		// 帶上 cookie + 對應 X-XSRF-TOKEN header 的變更請求應通過 CSRF 檢查。
		mockMvc.perform(post("/api/auth/register")
						.cookie(xsrf)
						.header("X-XSRF-TOKEN", xsrf.getValue())
						.contentType("application/json")
						.content(registerJson("csrf-test-" + UUID.randomUUID() + "@example.com")))
				.andExpect(status().isCreated());
	}

	@Test
	void wrongCsrfTokenIsForbidden() throws Exception {
		MvcResult primed = mockMvc.perform(get("/api/health")).andReturn();
		Cookie xsrf = primed.getResponse().getCookie("XSRF-TOKEN");
		assertThat(xsrf).isNotNull();

		mockMvc.perform(post("/api/auth/register")
						.cookie(xsrf)
						.header("X-XSRF-TOKEN", "not-the-real-token")
						.contentType("application/json")
						.content(registerJson("csrf-test-" + UUID.randomUUID() + "@example.com")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
	}

	private static String registerJson(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"Password123!\",\"displayName\":\"CSRF 測試\"}";
	}
}
