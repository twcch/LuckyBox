package com.luckybox.checkin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CheckInRepository {

	private final JdbcTemplate jdbcTemplate;

	CheckInRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	long ensureWallet(long userId) {
		return findWalletId(userId).orElseGet(() -> {
			String now = Instant.now().toString();
			jdbcTemplate.update("""
					INSERT OR IGNORE INTO wallets (
						user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at
					)
					VALUES (?, 0, 0, 0, ?, ?)
					""", userId, now, now);
			return findWalletId(userId).orElseThrow(() -> new IllegalStateException("Wallet was not created"));
		});
	}

	boolean hasCheckedIn(long userId, LocalDate date) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM daily_check_ins WHERE user_id = ? AND check_in_date = ?",
				Integer.class, userId, date.toString());
		return count != null && count > 0;
	}

	int totalCheckIns(long userId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM daily_check_ins WHERE user_id = ?", Integer.class, userId);
		return count == null ? 0 : count;
	}

	List<LocalDate> recentCheckInDates(long userId, int limit) {
		return jdbcTemplate.query("""
				SELECT check_in_date
				FROM daily_check_ins
				WHERE user_id = ?
				ORDER BY check_in_date DESC
				LIMIT ?
				""", (rs, rowNum) -> LocalDate.parse(rs.getString("check_in_date")), userId, limit);
	}

	/**
	 * 嘗試建立今日簽到紀錄；若已存在（UNIQUE 衝突）回傳空 Optional 代表今天已簽到。
	 */
	Optional<Long> insertCheckIn(long userId, LocalDate date, int rewardAmount) {
		String now = Instant.now().toString();
		try {
			int rows = jdbcTemplate.update("""
					INSERT INTO daily_check_ins (user_id, check_in_date, reward_amount, point_kind, created_at)
					VALUES (?, ?, ?, 'BONUS', ?)
					""", userId, date.toString(), Math.max(0, rewardAmount), now);
			if (rows == 0) {
				return Optional.empty();
			}
		} catch (DataAccessException ex) {
			if (isDuplicateCheckIn(ex)) {
				return Optional.empty();
			}
			throw ex;
		}
		Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return Optional.ofNullable(id);
	}

	void addCheckInBonus(long userId, long walletId, long checkInId, int amount) {
		if (amount <= 0) {
			return;
		}
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE wallets
				SET bonus_point_balance = bonus_point_balance + ?, updated_at = ?
				WHERE id = ? AND user_id = ?
				""", amount, now, walletId, userId);
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE id = ? AND user_id = ?",
				Integer.class, walletId, userId);
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, 'CHECK_IN_BONUS', ?, 'BONUS', ?, 'DailyCheckIn', ?, '每日簽到獎勵', ?, ?)
				""", userId, walletId, amount, balanceAfter == null ? 0 : balanceAfter, checkInId, userId, now);
	}

	private boolean isDuplicateCheckIn(DataAccessException ex) {
		String message = ex.getMessage();
		return message != null && message.toLowerCase().contains("unique") && message.contains("daily_check_ins");
	}

	private Optional<Long> findWalletId(long userId) {
		List<Long> walletIds = jdbcTemplate.query(
				"SELECT id FROM wallets WHERE user_id = ?", (rs, rowNum) -> rs.getLong("id"), userId);
		return walletIds.stream().findFirst();
	}
}
