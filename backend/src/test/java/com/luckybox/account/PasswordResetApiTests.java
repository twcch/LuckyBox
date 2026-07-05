package com.luckybox.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'reset-%@example.com')");
		jdbcTemplate.update("DELETE FROM wallet_ledger WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'reset-%@example.com')");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'reset-%@example.com')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'reset-%@example.com'");
	}

	@Test
	void forgotPasswordCreatesTokenForActiveUser() throws Exception {
		String email = registerUser();
		long userId = userIdByEmail(email);

		mockMvc.perform(post("/api/auth/forgot-password")
						.contentType("application/json")
						.content("""
								{"email":"%s"}
								""".formatted(email)))
				.andExpect(status().isAccepted());

		Integer tokens = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, userId);
		org.assertj.core.api.Assertions.assertThat(tokens).isEqualTo(1);
	}

	@Test
	void forgotPasswordReturnsAcceptedForUnknownEmailWithoutCreatingToken() throws Exception {
		mockMvc.perform(post("/api/auth/forgot-password")
						.contentType("application/json")
						.content("""
								{"email":"reset-unknown-%s@example.com"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isAccepted());
	}

	@Test
	void resetPasswordWithValidTokenChangesPassword() throws Exception {
		String email = registerUser();
		long userId = userIdByEmail(email);
		String rawToken = "valid-" + UUID.randomUUID();
		insertToken(userId, rawToken, Instant.now().plusSeconds(600).toString());

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType("application/json")
						.content("""
								{"token":"%s","password":"NewPassw0rd9"}
								""".formatted(rawToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"NewPassw0rd9"}
								""".formatted(email)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void resetPasswordRejectsInvalidToken() throws Exception {
		mockMvc.perform(post("/api/auth/reset-password")
						.contentType("application/json")
						.content("""
								{"token":"does-not-exist","password":"NewPassw0rd9"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
	}

	@Test
	void resetPasswordRejectsExpiredToken() throws Exception {
		String email = registerUser();
		long userId = userIdByEmail(email);
		String rawToken = "expired-" + UUID.randomUUID();
		insertToken(userId, rawToken, Instant.now().minusSeconds(60).toString());

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType("application/json")
						.content("""
								{"token":"%s","password":"NewPassw0rd9"}
								""".formatted(rawToken)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
	}

	@Test
	void resetTokenIsSingleUse() throws Exception {
		String email = registerUser();
		long userId = userIdByEmail(email);
		String rawToken = "single-" + UUID.randomUUID();
		insertToken(userId, rawToken, Instant.now().plusSeconds(600).toString());

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType("application/json")
						.content("""
								{"token":"%s","password":"NewPassw0rd9"}
								""".formatted(rawToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/reset-password")
						.contentType("application/json")
						.content("""
								{"token":"%s","password":"AnotherPass9"}
								""".formatted(rawToken)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
	}

	private String registerUser() throws Exception {
		String email = "reset-" + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/auth/register")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"Password123!","displayName":"重設測試玩家"}
								""".formatted(email)))
				.andExpect(status().isCreated());
		return email;
	}

	private long userIdByEmail(String email) {
		Long id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?)", Long.class, email);
		return id == null ? 0 : id;
	}

	private void insertToken(long userId, String rawToken, String expiresAt) {
		jdbcTemplate.update("""
				INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used_at, created_at)
				VALUES (?, ?, ?, NULL, ?)
				""", userId, sha256Hex(rawToken), expiresAt, Instant.now().toString());
	}

	private static String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
