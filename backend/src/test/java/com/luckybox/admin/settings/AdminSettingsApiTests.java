package com.luckybox.admin.settings;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSettingsApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void adminCanReadSettingsSummaryWithoutSecrets() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/admin/settings").session(loginAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sections.length()").value(5))
				.andExpect(jsonPath("$.sections[?(@.key == 'security')].items[?(@.key == 'csrf')].value").isNotEmpty())
				.andExpect(jsonPath("$.sections[?(@.key == 'payment')].items[?(@.key == 'provider')].value").isNotEmpty())
				.andExpect(jsonPath("$.sections[?(@.key == 'mail')].items[?(@.key == 'smtpHost')].value").isNotEmpty())
				.andExpect(jsonPath("$.sections[?(@.key == 'promo')].items[?(@.key == 'dailyCheckInStreakBonuses')].value").isNotEmpty())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("mock-webhook-secret");
		assertThat(body).doesNotContain("hash-key");
		assertThat(body).doesNotContain("password");
	}

	@Test
	void normalUserCannotReadSettingsSummary() throws Exception {
		mockMvc.perform(get("/api/admin/settings").session(registerUser()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
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
		String email = "admin-settings-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台設定測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
