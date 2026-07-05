package com.luckybox.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AuthApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void registersUserAndKeepsSession() throws Exception {
		String email = "phase3-" + UUID.randomUUID() + "@example.com";

		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "Phase 3 玩家",
								  "phone": "0912345678"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.cashPointBalance").value(0))
				.andReturn();

		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void rejectsInvalidLoginAndDuplicateEmail() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"missing@example.com","password":"bad-password"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "admin@luckybox.local",
								  "password": "Password123!",
								  "displayName": "Duplicate"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void logsInSeededAdminAndLogsOut() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"admin@luckybox.local","password":"ChangeMe123!"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
				.andReturn();

		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
	}

	@Test
	void updatesCurrentUserProfile() throws Exception {
		String email = "profile-" + UUID.randomUUID() + "@example.com";
		MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "原本名稱",
								  "phone": "0911111111"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);
		mockMvc.perform(get("/api/account/profile").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.displayName").value("原本名稱"))
				.andExpect(jsonPath("$.phone").value("0911111111"));

		mockMvc.perform(patch("/api/account/profile")
						.session(session)
						.contentType("application/json")
						.content("""
								{"displayName":"更新後玩家","phone":"0922222222"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("更新後玩家"))
				.andExpect(jsonPath("$.phone").value("0922222222"));

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("更新後玩家"));

		mockMvc.perform(put("/api/account/profile")
						.session(session)
						.contentType("application/json")
						.content("""
								{"displayName":"相容更新玩家","phone":"0933333333"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("相容更新玩家"))
				.andExpect(jsonPath("$.phone").value("0933333333"));
	}

	@Test
	void managesCurrentUserAddresses() throws Exception {
		String email = "address-" + UUID.randomUUID() + "@example.com";
		MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "地址測試玩家"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();

		MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);
		MvcResult createResult = mockMvc.perform(post("/api/account/addresses")
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "recipientName": "王小明",
								  "phone": "0912345678",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "中正區",
								  "addressLine": "忠孝西路一段 1 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.defaultAddress").value(true))
				.andReturn();

		int addressId = ((Number) com.jayway.jsonpath.JsonPath
				.read(createResult.getResponse().getContentAsString(), "$.id")).intValue();

		mockMvc.perform(put("/api/account/addresses/{addressId}", addressId)
						.session(session)
						.contentType("application/json")
						.content("""
								{
								  "recipientName": "王小明",
								  "phone": "0987654321",
								  "postalCode": "100",
								  "city": "台北市",
								  "district": "大安區",
								  "addressLine": "復興南路一段 1 號",
								  "defaultAddress": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.district").value("大安區"));

		mockMvc.perform(get("/api/account/addresses").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(delete("/api/account/addresses/{addressId}", addressId).session(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/account/addresses").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}
}
