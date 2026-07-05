package com.luckybox.coupon;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.luckybox.vip.VipTiers;

@Repository
class CouponRepository {

	private final JdbcTemplate jdbcTemplate;

	CouponRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<CouponSummary> findAvailableCoupons(long userId) {
		String now = Instant.now().toString();
		return jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at
				FROM coupons
				WHERE status = 'ACTIVE'
					AND (starts_at IS NULL OR starts_at <= ?)
					AND (ends_at IS NULL OR ends_at >= ?)
					AND (usage_limit IS NULL OR used_count < usage_limit)
					AND NOT EXISTS (
						SELECT 1
						FROM coupon_usages cu
						WHERE cu.coupon_id = coupons.id
						  AND cu.user_id = ?
						  AND cu.status = 'APPLIED'
					)
					AND (
						vip_tier IS NULL OR vip_tier = ''
						OR (
							CASE COALESCE((SELECT vip_level FROM users WHERE id = ?), 'REGULAR')
								WHEN 'PLATINUM' THEN 3
								WHEN 'GOLD' THEN 2
								WHEN 'SILVER' THEN 1
								ELSE 0
							END
							>=
							CASE vip_tier
								WHEN 'PLATINUM' THEN 3
								WHEN 'GOLD' THEN 2
								WHEN 'SILVER' THEN 1
								ELSE 0
							END
						)
					)
				ORDER BY id DESC
				LIMIT 100
				""", (rs, rowNum) -> {
			int usageLimit = rs.getInt("usage_limit");
			boolean usageLimitNull = rs.wasNull();
			String type = rs.getString("type");
			String vipTier = rs.getString("vip_tier");
			return new CouponSummary(
					rs.getLong("id"),
					rs.getString("code"),
					type,
					typeLabel(type),
					vipTier,
					vipTierLabel(vipTier),
					rs.getInt("value"),
					rs.getInt("min_spend"),
					usageLimitNull ? null : usageLimit,
					rs.getInt("used_count"),
					rs.getString("starts_at"),
					rs.getString("ends_at"));
		}, now, now, userId, userId);
	}

	Optional<CouponForRedeem> findCoupon(long couponId) {
		List<CouponForRedeem> coupons = jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status
				FROM coupons
				WHERE id = ?
				""", (rs, rowNum) -> {
			Integer usageLimit = rs.getObject("usage_limit") == null ? null : rs.getInt("usage_limit");
			return new CouponForRedeem(
					rs.getLong("id"),
					rs.getString("code"),
					rs.getString("type"),
					rs.getString("vip_tier"),
					rs.getInt("value"),
					rs.getInt("min_spend"),
					usageLimit,
					rs.getInt("used_count"),
					rs.getString("starts_at"),
					rs.getString("ends_at"),
					rs.getString("status"));
		}, couponId);
		return coupons.stream().findFirst();
	}

	boolean hasUserUsedCoupon(long userId, long couponId) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM coupon_usages
				WHERE user_id = ? AND coupon_id = ? AND status = 'APPLIED'
				""", Integer.class, userId, couponId);
		return count != null && count > 0;
	}

	long ensureWallet(long userId) {
		List<Long> walletIds = jdbcTemplate.query("""
				SELECT id FROM wallets WHERE user_id = ?
				""", (rs, rowNum) -> rs.getLong("id"), userId);
		if (!walletIds.isEmpty()) {
			return walletIds.getFirst();
		}
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT OR IGNORE INTO wallets (
					user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at
				)
				VALUES (?, 0, 0, 0, ?, ?)
				""", userId, now, now);
		return jdbcTemplate.queryForObject("SELECT id FROM wallets WHERE user_id = ?", Long.class, userId);
	}

	boolean incrementCouponUsedCount(long couponId) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE coupons
				SET used_count = used_count + 1, updated_at = ?
				WHERE id = ?
				  AND status = 'ACTIVE'
				  AND (starts_at IS NULL OR starts_at <= ?)
				  AND (ends_at IS NULL OR ends_at >= ?)
				  AND (usage_limit IS NULL OR used_count < usage_limit)
				""", now, couponId, now, now);
		return rows == 1;
	}

	WalletBalance addBonusPointsFromCoupon(long userId, long walletId, long couponId, String code, int amount) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE wallets
				SET bonus_point_balance = bonus_point_balance + ?, updated_at = ?
				WHERE id = ? AND user_id = ?
				""", amount, now, walletId, userId);
		WalletBalance wallet = walletBalance(userId);
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, 'COUPON_BONUS', ?, 'BONUS', ?, 'Coupon', ?, ?, ?, ?)
				""",
				userId,
				walletId,
				amount,
				wallet.bonusPointBalance(),
				couponId,
				"優惠券贈點 " + code,
				userId,
				now);
		return wallet;
	}

	String createPointBonusUsage(long userId, long couponId, int pointAmount) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO coupon_usages (
					coupon_id, user_id, reference_type, reference_id, discount_amount, point_amount, status, used_at
				)
				VALUES (?, ?, 'Coupon', ?, 0, ?, 'APPLIED', ?)
				""", couponId, userId, couponId, pointAmount, now);
		return now;
	}

	WalletBalance walletBalance(long userId) {
		return jdbcTemplate.queryForObject("""
				SELECT cash_point_balance, bonus_point_balance, locked_balance
				FROM wallets
				WHERE user_id = ?
				""", (rs, rowNum) -> {
			int cash = rs.getInt("cash_point_balance");
			int bonus = rs.getInt("bonus_point_balance");
			int locked = rs.getInt("locked_balance");
			return new WalletBalance(bonus, cash + bonus - locked);
		}, userId);
	}

	private static String typeLabel(String type) {
		return switch (type) {
			case "POINT_BONUS" -> "贈點券";
			case "DISCOUNT" -> "折扣券";
			case "FREE_SHIPPING" -> "免運券";
			default -> type;
		};
	}

	static String vipTierLabel(String vipTier) {
		return VipTiers.couponRequirementLabel(vipTier);
	}

	record CouponForRedeem(
			long id,
			String code,
			String type,
			String vipTier,
			int value,
			int minSpend,
			Integer usageLimit,
			int usedCount,
			String startsAt,
			String endsAt,
			String status) {
	}

	record WalletBalance(int bonusPointBalance, int totalAvailableBalance) {
	}
}
