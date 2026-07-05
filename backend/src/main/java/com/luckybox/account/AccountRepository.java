package com.luckybox.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {

	private final JdbcTemplate jdbcTemplate;

	AccountRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<AuthUser> findUserById(long userId) {
		List<AuthUser> users = jdbcTemplate.query("""
				SELECT
					u.id, u.email, u.display_name, u.phone, u.role, u.status, u.vip_level,
					COALESCE(w.cash_point_balance, 0) AS cash_point_balance,
					COALESCE(w.bonus_point_balance, 0) AS bonus_point_balance
				FROM users u
				LEFT JOIN wallets w ON w.user_id = u.id
				WHERE u.id = ?
				""", (rs, rowNum) -> mapUser(rs), userId);
		return users.stream().findFirst();
	}

	Optional<AuthUser> findUserByEmail(String email) {
		List<AuthUser> users = jdbcTemplate.query("""
				SELECT
					u.id, u.email, u.display_name, u.phone, u.role, u.status, u.vip_level,
					COALESCE(w.cash_point_balance, 0) AS cash_point_balance,
					COALESCE(w.bonus_point_balance, 0) AS bonus_point_balance
				FROM users u
				LEFT JOIN wallets w ON w.user_id = u.id
				WHERE lower(u.email) = lower(?)
				""", (rs, rowNum) -> mapUser(rs), email);
		return users.stream().findFirst();
	}

	Optional<String> findPasswordHashByEmail(String email) {
		List<String> hashes = jdbcTemplate.query("""
				SELECT password_hash FROM users WHERE lower(email) = lower(?)
				""", (rs, rowNum) -> rs.getString("password_hash"), email);
		return hashes.stream().findFirst();
	}

	long createUser(RegisterRequest request, String passwordHash) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO users (
					email, phone, password_hash, display_name, avatar_url, role, status, vip_level,
					created_at, updated_at, last_login_at
				)
				VALUES (?, ?, ?, ?, NULL, 'USER', 'ACTIVE', 'REGULAR', ?, ?, NULL)
				""",
				request.email().trim().toLowerCase(),
				blankToNull(request.phone()),
				passwordHash,
				request.displayName().trim(),
				now,
				now);
		Long userId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return userId == null ? 0 : userId;
	}

	void createWallet(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO wallets (user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at)
				VALUES (?, 0, 0, 0, ?, ?)
				""", userId, now, now);
	}

	void updateLastLogin(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?", now, now, userId);
	}

	Optional<TotpState> findTotpState(long userId) {
		List<TotpState> states = jdbcTemplate.query(
				"SELECT totp_secret, totp_enabled FROM users WHERE id = ?",
				(rs, rowNum) -> new TotpState(rs.getString("totp_secret"), rs.getInt("totp_enabled") == 1),
				userId);
		return states.stream().findFirst();
	}

	void saveTotpSecret(long userId, String secret) {
		String now = Instant.now().toString();
		jdbcTemplate.update(
				"UPDATE users SET totp_secret = ?, totp_enabled = 0, updated_at = ? WHERE id = ?", secret, now, userId);
	}

	void setTotpEnabled(long userId, boolean enabled) {
		String now = Instant.now().toString();
		jdbcTemplate.update(
				"UPDATE users SET totp_enabled = ?, updated_at = ? WHERE id = ?", enabled ? 1 : 0, now, userId);
	}

	void clearTotp(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update(
				"UPDATE users SET totp_secret = NULL, totp_enabled = 0, updated_at = ? WHERE id = ?", now, userId);
	}

	boolean updateProfile(long userId, ProfileRequest request) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE users
				SET display_name = ?, phone = ?, updated_at = ?
				WHERE id = ?
				""",
				request.displayName().trim(),
				blankToNull(request.phone()),
				now,
				userId);
		return rows > 0;
	}

	List<AddressResponse> findAddresses(long userId) {
		return jdbcTemplate.query("""
				SELECT id, recipient_name, phone, postal_code, city, district, address_line, is_default
				FROM user_addresses
				WHERE user_id = ?
				ORDER BY is_default DESC, id DESC
				""", (rs, rowNum) -> mapAddress(rs), userId);
	}

	long createAddress(long userId, AddressRequest request, boolean defaultAddress) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO user_addresses (
					user_id, recipient_name, phone, postal_code, city, district, address_line, is_default, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				userId,
				request.recipientName().trim(),
				request.phone().trim(),
				blankToNull(request.postalCode()),
				request.city().trim(),
				request.district().trim(),
				request.addressLine().trim(),
				defaultAddress ? 1 : 0,
				now,
				now);
		Long addressId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return addressId == null ? 0 : addressId;
	}

	boolean updateAddress(long userId, long addressId, AddressRequest request, boolean defaultAddress) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE user_addresses
				SET recipient_name = ?, phone = ?, postal_code = ?, city = ?, district = ?, address_line = ?,
					is_default = ?, updated_at = ?
				WHERE id = ? AND user_id = ?
				""",
				request.recipientName().trim(),
				request.phone().trim(),
				blankToNull(request.postalCode()),
				request.city().trim(),
				request.district().trim(),
				request.addressLine().trim(),
				defaultAddress ? 1 : 0,
				now,
				addressId,
				userId);
		return rows > 0;
	}

	boolean deleteAddress(long userId, long addressId) {
		int rows = jdbcTemplate.update("DELETE FROM user_addresses WHERE id = ? AND user_id = ?", addressId, userId);
		return rows > 0;
	}

	void clearDefaultAddress(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("UPDATE user_addresses SET is_default = 0, updated_at = ? WHERE user_id = ?", now, userId);
	}

	int countAddresses(long userId) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_addresses WHERE user_id = ?", Integer.class, userId);
		return count == null ? 0 : count;
	}

	boolean hasDefaultAddress(long userId) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM user_addresses WHERE user_id = ? AND is_default = 1
				""", Integer.class, userId);
		return count != null && count > 0;
	}

	void makeNewestAddressDefault(long userId) {
		Long addressId = jdbcTemplate.queryForObject("""
				SELECT id FROM user_addresses WHERE user_id = ? ORDER BY id DESC LIMIT 1
				""", Long.class, userId);
		if (addressId != null) {
			clearDefaultAddress(userId);
			String now = Instant.now().toString();
			jdbcTemplate.update("UPDATE user_addresses SET is_default = 1, updated_at = ? WHERE id = ? AND user_id = ?",
					now, addressId, userId);
		}
	}

	private static AuthUser mapUser(ResultSet rs) throws SQLException {
		return new AuthUser(
				rs.getLong("id"),
				rs.getString("email"),
				rs.getString("display_name"),
				rs.getString("phone"),
				rs.getString("role"),
				rs.getString("status"),
				rs.getString("vip_level"),
				rs.getInt("cash_point_balance"),
				rs.getInt("bonus_point_balance"));
	}

	private static AddressResponse mapAddress(ResultSet rs) throws SQLException {
		return new AddressResponse(
				rs.getLong("id"),
				rs.getString("recipient_name"),
				rs.getString("phone"),
				rs.getString("postal_code"),
				rs.getString("city"),
				rs.getString("district"),
				rs.getString("address_line"),
				rs.getInt("is_default") == 1);
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
