package com.luckybox.admin.walletledger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminWalletLedgerRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminWalletLedgerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminWalletLedgerResponse> findLedger(String type, String pointKind, String referenceType, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (type != null) {
			where.append("AND l.type = ?\n");
			params.add(type);
		}
		if (pointKind != null) {
			where.append("AND l.point_kind = ?\n");
			params.add(pointKind);
		}
		if (referenceType != null) {
			where.append("AND lower(l.reference_type) = lower(?)\n");
			params.add(referenceType);
		}
		if (keyword != null) {
			String normalizedKeyword = keyword.toLowerCase();
			if (isDigits(normalizedKeyword)) {
				where.append("""
						AND (
							CAST(l.id AS TEXT) = ?
							OR CAST(l.reference_id AS TEXT) = ?
						)
						""");
				params.add(normalizedKeyword);
				params.add(normalizedKeyword);
			}
			else {
				where.append("""
						AND (
							lower(u.email) LIKE ?
							OR lower(u.display_name) LIKE ?
							OR lower(l.type) LIKE ?
							OR lower(l.reason) LIKE ?
							OR lower(l.reference_type) LIKE ?
						)
						""");
				String like = "%" + normalizedKeyword + "%";
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
			}
		}
		return jdbcTemplate.query("""
				SELECT
					l.id,
					l.user_id,
					u.display_name,
					u.email,
					l.type,
					l.amount,
					l.point_kind,
					l.balance_after,
					l.reference_type,
					l.reference_id,
					l.reason,
					l.created_by,
					created_by_user.display_name AS created_by_display_name,
					l.created_at
				FROM wallet_ledger l
				JOIN users u ON u.id = l.user_id
				LEFT JOIN users created_by_user ON created_by_user.id = l.created_by
				""" + where + """
				ORDER BY l.id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapLedger(rs), params.toArray());
	}

	boolean userExists(long userId) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
		return count != null && count > 0;
	}

	/**
	 * 人工調整指定會員的某類點數，正數加點、負數扣點。以條件 UPDATE 保證扣點後不會出現負餘額
	 * （影響列數為 0 即代表餘額不足），成功後寫入 ADJUSTMENT 流水並回傳該筆紀錄。
	 */
	Optional<AdminWalletLedgerResponse> applyAdjustment(
			long userId, String pointKind, int amount, String reason, long adminId) {
		long walletId = ensureWallet(userId);
		// pointKind 已於 service 層白名單化，僅可能是 CASH 或 BONUS，無 SQL 注入風險。
		String column = "CASH".equals(pointKind) ? "cash_point_balance" : "bonus_point_balance";
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update(
				"UPDATE wallets SET " + column + " = " + column + " + ?, updated_at = ? "
						+ "WHERE id = ? AND user_id = ? AND " + column + " + ? >= 0",
				amount, now, walletId, userId, amount);
		if (rows == 0) {
			return Optional.empty();
		}
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT " + column + " FROM wallets WHERE id = ? AND user_id = ?", Integer.class, walletId, userId);
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, 'ADJUSTMENT', ?, ?, ?, 'Adjustment', NULL, ?, ?, ?)
				""", userId, walletId, amount, pointKind, balanceAfter == null ? 0 : balanceAfter, reason, adminId, now);
		Long ledgerId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return findLedgerById(ledgerId == null ? 0 : ledgerId);
	}

	private long ensureWallet(long userId) {
		Long existing = walletId(userId);
		if (existing != null) {
			return existing;
		}
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT OR IGNORE INTO wallets (
					user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at
				)
				VALUES (?, 0, 0, 0, ?, ?)
				""", userId, now, now);
		Long created = walletId(userId);
		if (created == null) {
			throw new IllegalStateException("Wallet was not created for user " + userId);
		}
		return created;
	}

	private Long walletId(long userId) {
		return jdbcTemplate.query("SELECT id FROM wallets WHERE user_id = ?", (rs, rowNum) -> rs.getLong("id"), userId)
				.stream().findFirst().orElse(null);
	}

	private Optional<AdminWalletLedgerResponse> findLedgerById(long ledgerId) {
		List<AdminWalletLedgerResponse> rows = jdbcTemplate.query("""
				SELECT
					l.id,
					l.user_id,
					u.display_name,
					u.email,
					l.type,
					l.amount,
					l.point_kind,
					l.balance_after,
					l.reference_type,
					l.reference_id,
					l.reason,
					l.created_by,
					created_by_user.display_name AS created_by_display_name,
					l.created_at
				FROM wallet_ledger l
				JOIN users u ON u.id = l.user_id
				LEFT JOIN users created_by_user ON created_by_user.id = l.created_by
				WHERE l.id = ?
				""", (rs, rowNum) -> mapLedger(rs), ledgerId);
		return rows.stream().findFirst();
	}

	private static AdminWalletLedgerResponse mapLedger(ResultSet rs) throws SQLException {
		String type = rs.getString("type");
		String pointKind = rs.getString("point_kind");
		Long referenceId = rs.getObject("reference_id") == null ? null : rs.getLong("reference_id");
		Long createdByUserId = rs.getObject("created_by") == null ? null : rs.getLong("created_by");
		return new AdminWalletLedgerResponse(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("display_name"),
				maskEmail(rs.getString("email")),
				type,
				typeLabel(type),
				rs.getInt("amount"),
				pointKind,
				pointKindLabel(pointKind),
				rs.getInt("balance_after"),
				rs.getString("reference_type"),
				referenceId,
				rs.getString("reason"),
				createdByUserId,
				rs.getString("created_by_display_name"),
				rs.getString("created_at"));
	}

	private static String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return maskText(email, 2, 0);
		}
		String local = email.substring(0, at);
		String domain = email.substring(at + 1);
		return maskText(local, 2, 0) + "@" + maskDomain(domain);
	}

	private static String maskDomain(String domain) {
		if (domain == null || domain.isBlank()) {
			return "***";
		}
		int dot = domain.indexOf('.');
		if (dot <= 0) {
			return maskText(domain, 1, 0);
		}
		return maskText(domain.substring(0, dot), 1, 0) + domain.substring(dot);
	}

	private static String maskText(String value, int visiblePrefix, int visibleSuffix) {
		if (value == null || value.isBlank()) {
			return "";
		}
		if (value.length() <= visiblePrefix + visibleSuffix) {
			return "*".repeat(value.length());
		}
		String prefix = value.substring(0, Math.min(visiblePrefix, value.length()));
		String suffix = visibleSuffix == 0 ? "" : value.substring(value.length() - visibleSuffix);
		return prefix + "***" + suffix;
	}

	private static String typeLabel(String type) {
		return switch (type) {
			case "TOP_UP" -> "現金儲值";
			case "TOP_UP_BONUS" -> "儲值贈點";
			case "COUPON_BONUS" -> "優惠券贈點";
			case "FIRST_DEPOSIT_BONUS" -> "首儲贈點";
			case "SPEND_THRESHOLD_BONUS" -> "消費門檻紅利";
			case "CHECK_IN_BONUS" -> "每日簽到獎勵";
			case "DRAW_SPEND" -> "抽賞扣點";
			case "ADJUSTMENT" -> "人工調整";
			case "REFUND" -> "退款回補";
			case "COMPENSATION" -> "客服補償";
			default -> type;
		};
	}

	private static String pointKindLabel(String pointKind) {
		return switch (pointKind) {
			case "CASH" -> "現金點";
			case "BONUS" -> "紅利點";
			default -> pointKind;
		};
	}

	private static boolean isDigits(String value) {
		return !value.isBlank() && value.chars().allMatch(Character::isDigit);
	}
}
