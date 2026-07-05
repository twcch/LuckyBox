package com.luckybox.admin.draworder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminDrawOrderRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminDrawOrderRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminDrawOrderResponse> findOrders(String status, String campaignSlug, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND d.status = ?\n");
			params.add(status);
		}
		if (campaignSlug != null) {
			where.append("AND c.slug = ?\n");
			params.add(campaignSlug);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(u.email) LIKE ?
						OR lower(u.display_name) LIKE ?
						OR lower(c.slug) LIKE ?
						OR lower(c.title) LIKE ?
						OR CAST(d.id AS TEXT) = ?
					)
					""");
			String normalizedKeyword = keyword.toLowerCase();
			String like = "%" + normalizedKeyword + "%";
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(normalizedKeyword);
		}
		return jdbcTemplate.query("""
				SELECT
					d.id,
					d.user_id,
					u.email,
					u.display_name,
					c.slug AS campaign_slug,
					c.title AS campaign_title,
					d.quantity,
					d.point_spent,
					d.status,
					d.created_at,
					d.completed_at,
					(
						SELECT COUNT(*)
						FROM draw_results r
						WHERE r.draw_order_id = d.id
					) AS result_count,
					COALESCE((
						SELECT GROUP_CONCAT(result_text, '、')
						FROM (
							SELECT p.rank || '賞 ' || p.name AS result_text
							FROM draw_results r
							JOIN prizes p ON p.id = r.prize_id
							WHERE r.draw_order_id = d.id
							ORDER BY r.result_index
						)
					), '') AS prize_summary
				FROM draw_orders d
				JOIN users u ON u.id = d.user_id
				JOIN kuji_campaigns c ON c.id = d.campaign_id
				""" + where + """
				ORDER BY d.id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapOrder(rs), params.toArray());
	}

	Optional<AdminDrawOrderDetailResponse> findOrder(long orderId) {
		List<AdminDrawOrderDetailRow> rows = jdbcTemplate.query("""
				SELECT
					d.id,
					d.user_id,
					u.email,
					u.display_name,
					c.slug AS campaign_slug,
					c.title AS campaign_title,
					d.quantity,
					COALESCE(d.original_point_spent, d.point_spent) AS original_point_spent,
					COALESCE(d.discount_amount, 0) AS discount_amount,
					d.point_spent,
					cp.code AS coupon_code,
					d.status,
					d.idempotency_key,
					d.created_at,
					d.completed_at,
					(
						SELECT COUNT(*)
						FROM draw_results r
						WHERE r.draw_order_id = d.id
					) AS result_count,
					COALESCE((
						SELECT GROUP_CONCAT(result_text, '、')
						FROM (
							SELECT p.rank || '賞 ' || p.name AS result_text
							FROM draw_results r
							JOIN prizes p ON p.id = r.prize_id
							WHERE r.draw_order_id = d.id
							ORDER BY r.result_index
						)
					), '') AS prize_summary
				FROM draw_orders d
				JOIN users u ON u.id = d.user_id
				JOIN kuji_campaigns c ON c.id = d.campaign_id
				LEFT JOIN coupons cp ON cp.id = d.coupon_id
				WHERE d.id = ?
				""", (rs, rowNum) -> mapDetailRow(rs), orderId);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		AdminDrawOrderDetailRow row = rows.get(0);
		List<AdminDrawOrderResultResponse> results = jdbcTemplate.query("""
				SELECT
					r.id,
					r.result_index,
					t.serial_number,
					p.rank,
					p.name,
					p.is_last_prize,
					r.random_proof,
					r.created_at
				FROM draw_results r
				JOIN kuji_tickets t ON t.id = r.ticket_id
				JOIN prizes p ON p.id = r.prize_id
				WHERE r.draw_order_id = ?
				ORDER BY r.result_index
				""", (rs, rowNum) -> new AdminDrawOrderResultResponse(
				rs.getLong("id"),
				rs.getInt("result_index"),
				rs.getString("serial_number"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getInt("is_last_prize") == 1,
				rs.getString("random_proof"),
				rs.getString("created_at")), orderId);
		List<AdminDrawOrderLedgerResponse> ledgerRows = jdbcTemplate.query("""
				SELECT id, type, amount, point_kind, balance_after, reason, created_by, created_at
				FROM wallet_ledger
				WHERE reference_type = 'DrawOrder'
					AND reference_id = ?
					AND user_id = ?
				ORDER BY id
				""", (rs, rowNum) -> {
			long createdBy = rs.getLong("created_by");
			boolean createdByNull = rs.wasNull();
			return new AdminDrawOrderLedgerResponse(
					rs.getLong("id"),
					rs.getString("type"),
					ledgerTypeLabel(rs.getString("type")),
					rs.getInt("amount"),
					rs.getString("point_kind"),
					pointKindLabel(rs.getString("point_kind")),
					rs.getInt("balance_after"),
					rs.getString("reason"),
					createdByNull ? null : createdBy,
					rs.getString("created_at"));
		}, orderId, row.userId());
		return Optional.of(new AdminDrawOrderDetailResponse(
				row.id(),
				row.userId(),
				row.userDisplayName(),
				row.maskedUserEmail(),
				row.campaignSlug(),
				row.campaignTitle(),
				row.quantity(),
				row.originalPointSpent(),
				row.discountAmount(),
				row.pointSpent(),
				row.couponCode(),
				row.status(),
				row.statusLabel(),
				row.resultCount(),
				row.prizeSummary(),
				row.idempotencyKey(),
				row.createdAt(),
				row.completedAt(),
				results,
				ledgerRows));
	}

	private static AdminDrawOrderResponse mapOrder(ResultSet rs) throws SQLException {
		String status = rs.getString("status");
		return new AdminDrawOrderResponse(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("display_name"),
				maskEmail(rs.getString("email")),
				rs.getString("campaign_slug"),
				rs.getString("campaign_title"),
				rs.getInt("quantity"),
				rs.getInt("point_spent"),
				status,
				statusLabel(status),
				rs.getInt("result_count"),
				rs.getString("prize_summary"),
				rs.getString("created_at"),
				rs.getString("completed_at"));
	}

	private static AdminDrawOrderDetailRow mapDetailRow(ResultSet rs) throws SQLException {
		String status = rs.getString("status");
		return new AdminDrawOrderDetailRow(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("display_name"),
				maskEmail(rs.getString("email")),
				rs.getString("campaign_slug"),
				rs.getString("campaign_title"),
				rs.getInt("quantity"),
				rs.getInt("original_point_spent"),
				rs.getInt("discount_amount"),
				rs.getInt("point_spent"),
				rs.getString("coupon_code"),
				status,
				statusLabel(status),
				rs.getInt("result_count"),
				rs.getString("prize_summary"),
				rs.getString("idempotency_key"),
				rs.getString("created_at"),
				rs.getString("completed_at"));
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

	private static String statusLabel(String status) {
		return switch (status) {
			case "PENDING" -> "處理中";
			case "COMPLETED" -> "完成";
			case "FAILED" -> "失敗";
			case "REFUNDED" -> "已退款";
			default -> status;
		};
	}

	private static String ledgerTypeLabel(String type) {
		return switch (type) {
			case "DRAW_SPEND" -> "抽賞扣點";
			case "SPEND_THRESHOLD_BONUS" -> "消費門檻紅利";
			case "REFUND" -> "退款回補";
			case "ADJUSTMENT" -> "人工調整";
			case "COMPENSATION" -> "客服補償";
			case "COUPON_BONUS" -> "優惠券贈點";
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

	private record AdminDrawOrderDetailRow(
			long id,
			long userId,
			String userDisplayName,
			String maskedUserEmail,
			String campaignSlug,
			String campaignTitle,
			int quantity,
			int originalPointSpent,
			int discountAmount,
			int pointSpent,
			String couponCode,
			String status,
			String statusLabel,
			int resultCount,
			String prizeSummary,
			String idempotencyKey,
			String createdAt,
			String completedAt) {
	}
}
