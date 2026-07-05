package com.luckybox.admin.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class AdminAuditLogApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void deleteAdminAuditLogTestData() {
		jdbcTemplate.update("DELETE FROM audit_logs WHERE action LIKE 'AUDIT_TEST_%'");
		jdbcTemplate.update("""
				DELETE FROM audit_logs
				WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'admin-audit-user-%')
					OR entity_id IN (
						SELECT CAST(id AS TEXT)
						FROM users
						WHERE email LIKE 'admin-audit-user-%'
					)
				""");
		jdbcTemplate.update("""
				DELETE FROM wallets
				WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'admin-audit-user-%')
				""");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'admin-audit-user-%'");
	}

	@Test
	void adminListsAuditLogsWithFiltersAndMaskedActorEmail() throws Exception {
		long adminId = adminId();
		insertAuditLog(
				adminId,
				"SUPER_ADMIN",
				"AUDIT_TEST_UPDATED",
				"User",
				"123",
				"{\"status\":\"ACTIVE\"}",
				"{\"status\":\"SUSPENDED\",\"reason\":\"policy\"}");
		insertAuditLog(
				null,
				"SYSTEM",
				"AUDIT_TEST_SYSTEM",
				"PaymentOrder",
				"456",
				null,
				"{\"status\":\"PAID\"}");

		MvcResult result = mockMvc.perform(get("/api/admin/audit-logs")
						.session(loginAdmin())
						.param("action", "AUDIT_TEST_UPDATED")
						.param("entityType", "User")
						.param("actorRole", "SUPER_ADMIN")
						.param("q", "SUSPENDED")
						.param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].actorId").value((int) adminId))
				.andExpect(jsonPath("$[0].actorRole").value("SUPER_ADMIN"))
				.andExpect(jsonPath("$[0].actorRoleLabel").value("超級管理員"))
				.andExpect(jsonPath("$[0].maskedActorEmail").value("ad***@l***.local"))
				.andExpect(jsonPath("$[0].action").value("AUDIT_TEST_UPDATED"))
				.andExpect(jsonPath("$[0].actionLabel").value("AUDIT_TEST_UPDATED"))
				.andExpect(jsonPath("$[0].entityType").value("User"))
				.andExpect(jsonPath("$[0].entityTypeLabel").value("會員"))
				.andExpect(jsonPath("$[0].entityId").value("123"))
				.andExpect(jsonPath("$[0].beforeState").value("{\"status\":\"ACTIVE\"}"))
				.andExpect(jsonPath("$[0].afterState").value("{\"status\":\"SUSPENDED\",\"reason\":\"policy\"}"))
				.andExpect(jsonPath("$[0].ipAddress").value("127.0.0.1"))
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("admin@luckybox.local");

		mockMvc.perform(get("/api/admin/audit-logs")
						.session(loginAdmin())
						.param("action", "AUDIT_TEST_SYSTEM")
						.param("actorRole", "SYSTEM")
						.param("q", "PaymentOrder")
						.param("limit", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].actorRoleLabel").value("系統"))
				.andExpect(jsonPath("$[0].entityTypeLabel").value("付款訂單"));
	}

	@Test
	void normalUserCannotReadAdminAuditLogs() throws Exception {
		MockHttpSession userSession = registerUser();
		mockMvc.perform(get("/api/admin/audit-logs").session(userSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
	}

	@Test
	void adminCannotDeleteAuditLogsAndRecordRemains() throws Exception {
		long auditLogId = insertAuditLog(
				adminId(),
				"SUPER_ADMIN",
				"AUDIT_TEST_IMMUTABLE",
				"User",
				"789",
				"{\"status\":\"ACTIVE\"}",
				"{\"status\":\"SUSPENDED\"}");

		mockMvc.perform(delete("/api/admin/audit-logs/{auditLogId}", auditLogId).session(loginAdmin()))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("AUDIT_LOG_IMMUTABLE"));

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM audit_logs WHERE id = ? AND action = 'AUDIT_TEST_IMMUTABLE'",
				Integer.class,
				auditLogId);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void rejectsInvalidAuditLogFilters() throws Exception {
		MockHttpSession adminSession = loginAdmin();
		mockMvc.perform(get("/api/admin/audit-logs")
						.session(adminSession)
						.param("actorRole", "ROOT"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_AUDIT_ACTOR_ROLE"));

		mockMvc.perform(get("/api/admin/audit-logs")
						.session(adminSession)
						.param("limit", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_AUDIT_LIMIT"));
	}

	private long adminId() {
		Long adminId = jdbcTemplate.queryForObject(
				"SELECT id FROM users WHERE email = ?",
				Long.class,
				"admin@luckybox.local");
		return adminId == null ? 0 : adminId;
	}

	private long insertAuditLog(
			Long actorId,
			String actorRole,
			String action,
			String entityType,
			String entityId,
			String beforeState,
			String afterState) {
		jdbcTemplate.update("""
				INSERT INTO audit_logs (
					actor_id, actor_role, action, entity_type, entity_id, before_state, after_state, ip_address, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, '127.0.0.1', '2026-06-18T10:00:00Z')
				""", actorId, actorRole, action, entityType, entityId, beforeState, afterState);
		Long auditLogId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return auditLogId == null ? 0 : auditLogId;
	}

	private MockHttpSession registerUser() throws Exception {
		String email = "admin-audit-user-" + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{
								  "email": "%s",
								  "password": "Password123!",
								  "displayName": "審計查詢一般會員"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
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
}
