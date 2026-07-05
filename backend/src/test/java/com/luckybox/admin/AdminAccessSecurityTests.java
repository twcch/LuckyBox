package com.luckybox.admin;

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
class AdminAccessSecurityTests {

	private static final String[] ADMIN_GET_ENDPOINTS = {
			"/api/admin/dashboard",
			"/api/admin/campaigns",
			"/api/admin/users",
			"/api/admin/draw-orders",
			"/api/admin/draw-orders/1",
			"/api/admin/consistency",
	};

	@Autowired
	private MockMvc mockMvc;

	@Test
	void anonymousIsUnauthorizedOnAdminApis() throws Exception {
		for (String endpoint : ADMIN_GET_ENDPOINTS) {
			mockMvc.perform(get(endpoint))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		}
	}

	@Test
	void normalMemberIsForbiddenOnAdminApis() throws Exception {
		MockHttpSession session = registerUser();
		for (String endpoint : ADMIN_GET_ENDPOINTS) {
			mockMvc.perform(get(endpoint).session(session))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
		}
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "adminsec-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "後台安全測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
