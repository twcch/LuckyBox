package com.luckybox.accountorder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AccountOrderRepository {

	private final JdbcTemplate jdbcTemplate;

	AccountOrderRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AccountDrawOrderResponse> drawOrders(long userId) {
		List<DrawOrderRow> rows = jdbcTemplate.query("""
				SELECT
					d.id,
					c.slug AS campaign_slug,
					c.title AS campaign_title,
					d.quantity,
					COALESCE(d.original_point_spent, d.point_spent) AS original_point_spent,
					COALESCE(d.discount_amount, 0) AS discount_amount,
					d.point_spent,
					cp.code AS coupon_code,
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
				JOIN kuji_campaigns c ON c.id = d.campaign_id
				LEFT JOIN coupons cp ON cp.id = d.coupon_id
				WHERE d.user_id = ?
				ORDER BY d.id DESC
				LIMIT 50
				""", (rs, rowNum) -> new DrawOrderRow(
				rs.getLong("id"),
				rs.getString("campaign_slug"),
				rs.getString("campaign_title"),
				rs.getInt("quantity"),
				rs.getInt("original_point_spent"),
				rs.getInt("discount_amount"),
				rs.getInt("point_spent"),
				rs.getString("coupon_code"),
				rs.getString("status"),
				rs.getInt("result_count"),
				rs.getString("prize_summary"),
				rs.getString("created_at"),
				rs.getString("completed_at")), userId);
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> orderIds = rows.stream().map(DrawOrderRow::id).toList();
		Map<Long, List<AccountDrawOrderResultResponse>> resultsByOrder = drawResultsByOrder(orderIds);
		return rows.stream()
				.map(row -> mapDrawOrder(row, resultsByOrder.getOrDefault(row.id(), List.of())))
				.toList();
	}

	List<AccountPaymentOrderResponse> paymentOrders(long userId) {
		return jdbcTemplate.query("""
				SELECT
					id, merchant_trade_no, amount, point_amount, bonus_point_amount,
					status, created_at, paid_at
				FROM payment_orders
				WHERE user_id = ?
				ORDER BY id DESC
				LIMIT 50
				""", (rs, rowNum) -> {
			String status = rs.getString("status");
			return new AccountPaymentOrderResponse(
					rs.getLong("id"),
					rs.getString("merchant_trade_no"),
					rs.getInt("amount"),
					rs.getInt("point_amount"),
					rs.getInt("bonus_point_amount"),
					status,
					paymentStatusLabel(status),
					rs.getString("created_at"),
					rs.getString("paid_at"));
		}, userId);
	}

	private Map<Long, List<AccountDrawOrderResultResponse>> drawResultsByOrder(List<Long> orderIds) {
		String placeholders = orderIds.stream().map(id -> "?").collect(Collectors.joining(", "));
		String sql = "SELECT r.draw_order_id, r.id, t.serial_number, p.rank, p.name, p.is_last_prize "
				+ "FROM draw_results r "
				+ "JOIN kuji_tickets t ON t.id = r.ticket_id "
				+ "JOIN prizes p ON p.id = r.prize_id "
				+ "WHERE r.draw_order_id IN (" + placeholders + ") "
				+ "ORDER BY r.draw_order_id, r.result_index";
		Map<Long, List<AccountDrawOrderResultResponse>> grouped = new LinkedHashMap<>();
		jdbcTemplate.query(sql, rs -> {
			long drawOrderId = rs.getLong("draw_order_id");
			grouped.computeIfAbsent(drawOrderId, key -> new ArrayList<>()).add(new AccountDrawOrderResultResponse(
					rs.getLong("id"),
					rs.getString("serial_number"),
					rs.getString("rank"),
					rs.getString("name"),
					rs.getInt("is_last_prize") == 1));
		}, orderIds.toArray());
		return grouped;
	}

	private static AccountDrawOrderResponse mapDrawOrder(DrawOrderRow row, List<AccountDrawOrderResultResponse> results) {
		return new AccountDrawOrderResponse(
				row.id(),
				row.campaignSlug(),
				row.campaignTitle(),
				row.quantity(),
				row.originalPointSpent(),
				row.discountAmount(),
				row.pointSpent(),
				row.couponCode(),
				row.status(),
				drawStatusLabel(row.status()),
				row.resultCount(),
				row.prizeSummary(),
				row.createdAt(),
				row.completedAt(),
				results);
	}

	private static String drawStatusLabel(String status) {
		return switch (status) {
			case "PENDING" -> "處理中";
			case "COMPLETED" -> "完成";
			case "FAILED" -> "失敗";
			case "REFUNDED" -> "已退款";
			default -> status;
		};
	}

	private static String paymentStatusLabel(String status) {
		return switch (status) {
			case "PENDING" -> "待付款";
			case "PAID" -> "已付款";
			case "FAILED" -> "付款失敗";
			case "CANCELED" -> "已取消";
			case "REFUNDED" -> "已退款";
			default -> status;
		};
	}

	private record DrawOrderRow(
			long id,
			String campaignSlug,
			String campaignTitle,
			int quantity,
			int originalPointSpent,
			int discountAmount,
			int pointSpent,
			String couponCode,
			String status,
			int resultCount,
			String prizeSummary,
			String createdAt,
			String completedAt) {
	}
}
