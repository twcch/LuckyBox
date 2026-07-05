package com.luckybox.admin.coupon;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.luckybox.vip.VipTiers;

@Repository
class AdminCouponRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminCouponRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminCouponResponse> findCoupons(String status, String type, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND c.status = ?\n");
			params.add(status);
		}
		if (type != null) {
			where.append("AND c.type = ?\n");
			params.add(type);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(c.code) LIKE ?
						OR lower(c.type) LIKE ?
						OR lower(c.status) LIKE ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
		}
		return jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at
				FROM coupons c
				""" + where + """
				ORDER BY id DESC
				LIMIT 100
				""", (rs, rowNum) -> mapCoupon(rs), params.toArray());
	}

	AdminCouponResponse findCoupon(long couponId) {
		List<AdminCouponResponse> rows = jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at
				FROM coupons
				WHERE id = ?
				""", (rs, rowNum) -> mapCoupon(rs), couponId);
		return rows.stream().findFirst().orElse(null);
	}

	AdminCouponResponse findCouponByCode(String code) {
		List<AdminCouponResponse> rows = jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at
				FROM coupons
				WHERE code = ?
				""", (rs, rowNum) -> mapCoupon(rs), code);
		return rows.stream().findFirst().orElse(null);
	}

	long createCoupon(AdminCouponRequest request) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO coupons (code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
				""",
				cleanRequired(request.code()),
				cleanRequired(request.type()),
				cleanOptional(request.vipTier()),
				request.value(),
				request.minSpend(),
				request.usageLimit(),
				cleanOptional(request.startsAt()),
				cleanOptional(request.endsAt()),
				cleanRequired(request.status()),
				now,
				now);
		Long couponId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return couponId == null ? 0 : couponId;
	}

	boolean updateCoupon(long couponId, AdminCouponRequest request) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE coupons
				SET code = ?, type = ?, vip_tier = ?, value = ?, min_spend = ?, usage_limit = ?, starts_at = ?, ends_at = ?, status = ?, updated_at = ?
				WHERE id = ?
				""",
				cleanRequired(request.code()),
				cleanRequired(request.type()),
				cleanOptional(request.vipTier()),
				request.value(),
				request.minSpend(),
				request.usageLimit(),
				cleanOptional(request.startsAt()),
				cleanOptional(request.endsAt()),
				cleanRequired(request.status()),
				now,
				couponId) > 0;
	}

	private static AdminCouponResponse mapCoupon(ResultSet rs) throws SQLException {
		String type = rs.getString("type");
		String vipTier = rs.getString("vip_tier");
		String status = rs.getString("status");
		int usageLimit = rs.getInt("usage_limit");
		boolean usageLimitNull = rs.wasNull();
		return new AdminCouponResponse(
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
				rs.getString("ends_at"),
				status,
				statusLabel(status),
				rs.getString("created_at"),
				rs.getString("updated_at"));
	}

	static String cleanOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static String cleanRequired(String value) {
		return value == null ? "" : value.trim();
	}

	private static String typeLabel(String type) {
		return switch (type) {
			case "POINT_BONUS" -> "贈點券";
			case "DISCOUNT" -> "折扣券";
			case "FREE_SHIPPING" -> "免運券";
			default -> type;
		};
	}

	private static String vipTierLabel(String vipTier) {
		return VipTiers.couponRequirementLabel(vipTier);
	}

	static String statusLabel(String status) {
		return switch (status) {
			case "DRAFT" -> "草稿";
			case "ACTIVE" -> "啟用";
			case "ARCHIVED" -> "已封存";
			default -> status;
		};
	}
}
