package com.luckybox.admin.auditlog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminAuditLogRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminAuditLogRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminAuditLogResponse> findAuditLogs(
			String action,
			String entityType,
			String actorRole,
			String keyword,
			int limit) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (action != null) {
			where.append("AND lower(a.action) = lower(?)\n");
			params.add(action);
		}
		if (entityType != null) {
			where.append("AND lower(a.entity_type) = lower(?)\n");
			params.add(entityType);
		}
		if (actorRole != null) {
			where.append("AND upper(a.actor_role) = ?\n");
			params.add(actorRole);
		}
		if (keyword != null) {
			String normalizedKeyword = keyword.toLowerCase();
			if (isDigits(normalizedKeyword)) {
				where.append("""
						AND (
							CAST(a.id AS TEXT) = ?
							OR CAST(a.actor_id AS TEXT) = ?
							OR a.entity_id = ?
						)
						""");
				params.add(normalizedKeyword);
				params.add(normalizedKeyword);
				params.add(normalizedKeyword);
			}
			else {
				where.append("""
						AND (
							lower(a.action) LIKE ?
							OR lower(a.entity_type) LIKE ?
							OR lower(COALESCE(a.entity_id, '')) LIKE ?
							OR lower(COALESCE(a.before_state, '')) LIKE ?
							OR lower(COALESCE(a.after_state, '')) LIKE ?
							OR lower(COALESCE(a.actor_role, '')) LIKE ?
							OR lower(COALESCE(actor.display_name, '')) LIKE ?
							OR lower(COALESCE(actor.email, '')) LIKE ?
						)
						""");
				String like = "%" + normalizedKeyword + "%";
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
				params.add(like);
			}
		}
		params.add(limit);
		return jdbcTemplate.query("""
				SELECT
					a.id,
					a.actor_id,
					actor.display_name AS actor_display_name,
					actor.email AS actor_email,
					a.actor_role,
					a.action,
					a.entity_type,
					a.entity_id,
					a.before_state,
					a.after_state,
					a.ip_address,
					a.created_at
				FROM audit_logs a
				LEFT JOIN users actor ON actor.id = a.actor_id
				""" + where + """
				ORDER BY a.id DESC
				LIMIT ?
				""", (rs, rowNum) -> mapAuditLog(rs), params.toArray());
	}

	private static AdminAuditLogResponse mapAuditLog(ResultSet rs) throws SQLException {
		String actorRole = rs.getString("actor_role");
		String action = rs.getString("action");
		String entityType = rs.getString("entity_type");
		Long actorId = rs.getObject("actor_id") == null ? null : rs.getLong("actor_id");
		return new AdminAuditLogResponse(
				rs.getLong("id"),
				actorId,
				rs.getString("actor_display_name"),
				maskEmail(rs.getString("actor_email")),
				actorRole,
				actorRoleLabel(actorRole),
				action,
				actionLabel(action),
				entityType,
				entityTypeLabel(entityType),
				rs.getString("entity_id"),
				rs.getString("before_state"),
				rs.getString("after_state"),
				rs.getString("ip_address"),
				rs.getString("created_at"));
	}

	private static String actorRoleLabel(String actorRole) {
		if (actorRole == null || actorRole.isBlank()) {
			return "未知";
		}
		return switch (actorRole) {
			case "SYSTEM" -> "系統";
			case "SUPER_ADMIN" -> "超級管理員";
			case "ADMIN" -> "管理員";
			case "OPERATOR" -> "營運";
			case "CUSTOMER_SERVICE" -> "客服";
			case "USER" -> "會員";
			default -> actorRole;
		};
	}

	private static String actionLabel(String action) {
		return switch (action) {
			case "ADMIN_CAMPAIGN_CREATED" -> "後台建立賞池";
			case "ADMIN_CAMPAIGN_UPDATED" -> "後台更新賞池";
			case "ADMIN_CAMPAIGN_PUBLISHED" -> "後台發布賞池";
			case "ADMIN_CAMPAIGN_PAUSED" -> "後台暫停賞池";
			case "ADMIN_BANNER_CREATED" -> "後台建立 Banner";
			case "ADMIN_BANNER_UPDATED" -> "後台更新 Banner";
			case "ADMIN_COUPON_CREATED" -> "後台建立優惠券";
			case "ADMIN_COUPON_UPDATED" -> "後台更新優惠券";
			case "ADMIN_PRIZE_CREATED" -> "後台建立獎項";
			case "ADMIN_PRIZE_UPDATED" -> "後台更新獎項";
			case "ADMIN_NEWS_CREATED" -> "後台建立公告";
			case "ADMIN_NEWS_UPDATED" -> "後台更新公告";
			case "ADMIN_SHIPMENT_UPDATED" -> "後台更新出貨";
			case "ADMIN_TICKETS_GENERATED" -> "後台產生票券";
			case "ADMIN_USER_STATUS_UPDATED" -> "後台更新會員狀態";
			case "DEV_SEED_ADMIN" -> "開發種子管理員";
			case "DEV_SEED_CAMPAIGN" -> "開發種子賞池";
			case "DRAW_LAST_PRIZE_AWARDED" -> "最後賞發放";
			case "DRAW_ORDER_COMPLETED" -> "抽賞完成";
			case "MOCK_PAYMENT_COMPLETED" -> "Mock 付款完成";
			case "SHIPMENT_REQUESTED" -> "會員申請出貨";
			case "USER_PROFILE_UPDATED" -> "會員更新資料";
			case "USER_REGISTERED" -> "會員註冊";
			default -> action;
		};
	}

	private static String entityTypeLabel(String entityType) {
		return switch (entityType) {
			case "Banner" -> "Banner";
			case "Campaign", "KujiCampaign" -> "賞池";
			case "Coupon" -> "優惠券";
			case "DrawOrder" -> "抽賞訂單";
			case "PaymentOrder" -> "付款訂單";
			case "News" -> "公告";
			case "Prize" -> "獎項";
			case "Shipment" -> "出貨單";
			case "User" -> "會員";
			default -> entityType;
		};
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

	private static boolean isDigits(String value) {
		return !value.isBlank() && value.chars().allMatch(Character::isDigit);
	}
}
