package com.luckybox.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PasswordResetRepository {

	private final JdbcTemplate jdbcTemplate;

	PasswordResetRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<Long> findActiveUserIdByEmail(String email) {
		List<Long> ids = jdbcTemplate.query("""
				SELECT id FROM users WHERE lower(email) = lower(?) AND status = 'ACTIVE'
				""", (rs, rowNum) -> rs.getLong("id"), email);
		return ids.stream().findFirst();
	}

	void createToken(long userId, String tokenHash, String expiresAt) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used_at, created_at)
				VALUES (?, ?, ?, NULL, ?)
				""", userId, tokenHash, expiresAt, now);
	}

	Optional<ResetToken> findToken(String tokenHash) {
		List<ResetToken> rows = jdbcTemplate.query("""
				SELECT id, user_id, expires_at, used_at
				FROM password_reset_tokens
				WHERE token_hash = ?
				""", (rs, rowNum) -> new ResetToken(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("expires_at"),
				rs.getString("used_at")), tokenHash);
		return rows.stream().findFirst();
	}

	boolean markTokenUsed(long tokenId) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE password_reset_tokens SET used_at = ? WHERE id = ? AND used_at IS NULL
				""", now, tokenId) == 1;
	}

	void updatePassword(long userId, String passwordHash) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?
				""", passwordHash, now, userId);
	}

	record ResetToken(long id, long userId, String expiresAt, String usedAt) {
	}
}
