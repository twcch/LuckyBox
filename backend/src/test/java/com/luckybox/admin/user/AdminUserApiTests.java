package com.luckybox.admin.user;

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
class AdminUserApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminUserTestData() {
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE action IN ('ADMIN_USER_STATUS_UPDATED', 'ADMIN_USER_ROLE_UPDATED')
					OR actor_id IN (SELECT id FROM users WHERE email LIKE 'admin-user-test-%')
					OR entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-user-test-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-user-test-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-user-test-%'");
	}

	@Test
	void adminListsUsersWithMaskedContactAndUpdatesStatus() throws Exception {
		RegisteredUser user = registerUser("會員遮罩測試", "0912345678");
		MockHttpSession adminSession = loginAdmin();

		MvcResult listResult = mockMvc.perform(get("/api/admin/users")
						.session(adminSession)
						.param("role", "USER")
						.param("q", user.email()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) user.id()))
				.andExpect(jsonPath("$[0].displayName").value("會員遮罩測試"))
				.andExpect(jsonPath("$[0].role").value("USER"))
				.andExpect(jsonPath("$[0].roleLabel").value("會員"))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].statusLabel").value("啟用"))
				.andExpect(jsonPath("$[0].maskedEmail").value("ad***@e***.com"))
				.andExpect(jsonPath("$[0].maskedPhone").value("***678"))
				.andReturn();

		String responseBody = listResult.getResponse().getContentAsString();
		assertThat(responseBody).doesNotContain(user.email());
		assertThat(responseBody).doesNotContain("0912345678");

		mockMvc.perform(patch("/api/admin/users/{userId}/status", user.id())
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"SUSPENDED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) user.id()))
				.andExpect(jsonPath("$.status").value("SUSPENDED"))
				.andExpect(jsonPath("$.statusLabel").value("停權"));

		mockMvc.perform(get("/api/admin/users")
						.session(adminSession)
						.param("status", "SUSPENDED")
						.param("q", "會員遮罩測試"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) user.id()));

		mockMvc.perform(patch("/api/admin/users/{userId}/status", user.id())
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"ACTIVE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.statusLabel").value("啟用"));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE action = 'ADMIN_USER_STATUS_UPDATED'
					AND entity_type = 'User'
					AND entity_id = ?
				""", Integer.class, String.valueOf(user.id()));
		assertThat(auditCount).isEqualTo(2);
	}

	@Test
	void normalUserCannotUseAdminUserApi() throws Exception {
		MockHttpSession userSession = registerUser("一般會員", "0922222222").session();

		mockMvc.perform(get("/api/admin/users").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));

		mockMvc.perform(patch("/api/admin/users/{userId}/status", 1)
						.session(userSession)
						.contentType("application/json")
						.content("""
								{"status":"SUSPENDED"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void superAdminUpdatesAssignableRoleAndRecordsAudit() throws Exception {
		RegisteredUser user = registerUser("角色調整會員", "0944444444");
		MockHttpSession adminSession = loginAdmin();

		mockMvc.perform(patch("/api/admin/users/{userId}/role", user.id())
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"role":"customer_service"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) user.id()))
				.andExpect(jsonPath("$.role").value("CUSTOMER_SERVICE"))
				.andExpect(jsonPath("$.roleLabel").value("客服"));

		mockMvc.perform(get("/api/admin/users")
						.session(adminSession)
						.param("role", "CUSTOMER_SERVICE")
						.param("q", user.email()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value((int) user.id()));

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE action = 'ADMIN_USER_ROLE_UPDATED'
					AND entity_type = 'User'
					AND entity_id = ?
				""", Integer.class, String.valueOf(user.id()));
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	void onlySuperAdminCanUpdateRolesAndSuperAdminRoleCannotBeAssigned() throws Exception {
		RegisteredUser target = registerUser("角色目標會員", "0955555555");
		RegisteredUser admin = registerUser("一般管理員", "0966666666");
		jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.id());
		MockHttpSession adminSession = login(admin.email(), "Password123!");
		MockHttpSession superAdminSession = loginAdmin();

		mockMvc.perform(patch("/api/admin/users/{userId}/role", target.id())
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"role":"OPERATOR"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SUPER_ADMIN_REQUIRED"));

		mockMvc.perform(patch("/api/admin/users/{userId}/role", target.id())
						.session(superAdminSession)
						.contentType("application/json")
						.content("""
								{"role":"SUPER_ADMIN"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_USER_ROLE"));

		mockMvc.perform(patch("/api/admin/users/{userId}/role", 1)
						.session(superAdminSession)
						.contentType("application/json")
						.content("""
								{"role":"ADMIN"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_UPDATE_SELF"));
	}

	@Test
	void adminCannotUpdateSelfOrSuperAdmin() throws Exception {
		MockHttpSession adminSession = loginAdmin();

		mockMvc.perform(patch("/api/admin/users/{userId}/status", 1)
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"SUSPENDED"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_UPDATE_SELF"));
	}

	@Test
	void rejectsInvalidUserFiltersAndStatus() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		RegisteredUser user = registerUser("狀態驗證會員", "0933333333");

		mockMvc.perform(get("/api/admin/users").session(adminSession).param("status", "LOCKED"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_USER_STATUS"));

		mockMvc.perform(get("/api/admin/users").session(adminSession).param("role", "ROOT"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_USER_ROLE"));

		mockMvc.perform(patch("/api/admin/users/{userId}/status", user.id())
						.session(adminSession)
						.contentType("application/json")
						.content("""
								{"status":"DELETED"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_USER_STATUS"));
	}

	private MockHttpSession loginAdmin() throws Exception {
		return login("admin@luckybox.local", "ChangeMe123!");
	}

	private MockHttpSession login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private RegisteredUser registerUser(String displayName, String phone) throws Exception {
		String email = "admin-user-test-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "%s",
								  "phone": "%s"
								}
								""".formatted(email, displayName, phone)))
				.andExpect(status().isCreated())
				.andReturn();
		long userId = ((Number) com.jayway.jsonpath.JsonPath
				.read(result.getResponse().getContentAsString(), "$.id")).longValue();
		return new RegisteredUser(userId, email, (MockHttpSession) result.getRequest().getSession(false));
	}

	private record RegisteredUser(long id, String email, MockHttpSession session) {
	}
}
