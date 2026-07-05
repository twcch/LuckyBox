package com.luckybox.admin.payment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminPaymentOrderRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminPaymentOrderRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminPaymentOrderResponse> findPaymentOrders(String status, String provider, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND p.status = ?\n");
			params.add(status);
		}
		if (provider != null) {
			where.append("AND lower(p.provider) = lower(?)\n");
			params.add(provider);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(u.email) LIKE ?
						OR lower(u.display_name) LIKE ?
						OR lower(p.merchant_trade_no) LIKE ?
						OR CAST(p.id AS TEXT) = ?
					)
					""");
			String normalizedKeyword = keyword.toLowerCase();
			String like = "%" + normalizedKeyword + "%";
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(normalizedKeyword);
		}
		return jdbcTemplate.query("""
				SELECT
					p.id,
					p.user_id,
					u.display_name,
					u.email,
					p.provider,
					p.merchant_trade_no,
					p.amount,
					p.point_amount,
					p.bonus_point_amount,
					p.status,
					p.created_at,
					p.paid_at
				FROM payment_orders p
				JOIN users u ON u.id = p.user_id
				""" + where + """
				ORDER BY p.id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapPaymentOrder(rs), params.toArray());
	}

	RefundRow findRefundRow(long orderId) {
		List<RefundRow> rows = jdbcTemplate.query("""
				SELECT id, user_id, status, point_amount, bonus_point_amount
				FROM payment_orders
				WHERE id = ?
				""", (rs, rowNum) -> new RefundRow(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("status"),
				rs.getInt("point_amount"),
				rs.getInt("bonus_point_amount")), orderId);
		return rows.stream().findFirst().orElse(null);
	}

	/** 條件式將 PAID 轉 REFUNDED；回傳是否成功（0 列代表已非 PAID，防併發重複退款）。 */
	boolean markRefunded(long orderId) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE payment_orders
				SET status = 'REFUNDED', updated_at = ?
				WHERE id = ? AND status = 'PAID'
				""", now, orderId) > 0;
	}

	/**
	 * 回收此訂單原先入帳的某類點數（現金/紅利）。以條件 UPDATE 確保餘額足夠才扣（不足回傳 false，
	 * 由 service 在交易內回滾），成功後寫入負額 REFUND 流水。amount<=0 視為無需回收直接成功。
	 */
	boolean reverseWalletCredit(long userId, String pointKind, int amount, long orderId, String reason, long adminId) {
		if (amount <= 0) {
			return true;
		}
		Long walletId = walletIdForUser(userId);
		if (walletId == null) {
			return false;
		}
		// pointKind 由 service 控制僅為 CASH / BONUS，對應固定欄名，無 SQL 注入風險。
		String column = "CASH".equals(pointKind) ? "cash_point_balance" : "bonus_point_balance";
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update(
				"UPDATE wallets SET " + column + " = " + column + " - ?, updated_at = ? "
						+ "WHERE id = ? AND user_id = ? AND " + column + " >= ?",
				amount, now, walletId, userId, amount);
		if (rows == 0) {
			return false;
		}
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT " + column + " FROM wallets WHERE id = ? AND user_id = ?", Integer.class, walletId, userId);
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, 'REFUND', ?, ?, ?, 'PaymentOrder', ?, ?, ?, ?)
				""", userId, walletId, -amount, pointKind, balanceAfter == null ? 0 : balanceAfter,
				orderId, reason, adminId, now);
		return true;
	}

	AdminPaymentOrderResponse findResponse(long orderId) {
		List<AdminPaymentOrderResponse> rows = jdbcTemplate.query("""
				SELECT
					p.id, p.user_id, u.display_name, u.email, p.provider, p.merchant_trade_no,
					p.amount, p.point_amount, p.bonus_point_amount, p.status, p.created_at, p.paid_at
				FROM payment_orders p
				JOIN users u ON u.id = p.user_id
				WHERE p.id = ?
				""", (rs, rowNum) -> mapPaymentOrder(rs), orderId);
		return rows.stream().findFirst().orElse(null);
	}

	String findProviderPayload(long orderId) {
		List<String> rows = jdbcTemplate.query("""
				SELECT provider_payload
				FROM payment_orders
				WHERE id = ?
				""", (rs, rowNum) -> rs.getString("provider_payload"), orderId);
		return rows.stream().findFirst().orElse(null);
	}

	List<AdminPaymentWebhookEventResponse> findWebhookEvents(String provider, String merchantTradeNo) {
		return jdbcTemplate.query("""
				SELECT provider, event_id, merchant_trade_no, status, amount, processed, message,
					created_at, processed_at, raw_payload
				FROM payment_webhook_events
				WHERE upper(provider) = upper(?) AND merchant_trade_no = ?
				ORDER BY id DESC
				LIMIT 50
				""", (rs, rowNum) -> new AdminPaymentWebhookEventResponse(
				rs.getString("provider"),
				rs.getString("event_id"),
				rs.getString("merchant_trade_no"),
				rs.getString("status"),
				rs.getInt("amount"),
				rs.getInt("processed") == 1,
				rs.getString("message"),
				rs.getString("created_at"),
				rs.getString("processed_at"),
				rs.getString("raw_payload")), provider, merchantTradeNo);
	}

	private Long walletIdForUser(long userId) {
		return jdbcTemplate.query("SELECT id FROM wallets WHERE user_id = ?", (rs, rowNum) -> rs.getLong("id"), userId)
				.stream().findFirst().orElse(null);
	}

	record RefundRow(long id, long userId, String status, int pointAmount, int bonusPointAmount) {
	}

	private static AdminPaymentOrderResponse mapPaymentOrder(ResultSet rs) throws SQLException {
		String status = rs.getString("status");
		int pointAmount = rs.getInt("point_amount");
		int bonusPointAmount = rs.getInt("bonus_point_amount");
		return new AdminPaymentOrderResponse(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("display_name"),
				maskEmail(rs.getString("email")),
				rs.getString("provider"),
				rs.getString("merchant_trade_no"),
				rs.getInt("amount"),
				pointAmount,
				bonusPointAmount,
				pointAmount + bonusPointAmount,
				status,
				statusLabel(status),
				rs.getString("created_at"),
				rs.getString("paid_at"));
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
			case "PENDING" -> "待付款";
			case "PAID" -> "已付款";
			case "FAILED" -> "付款失敗";
			case "CANCELED" -> "已取消";
			case "REFUNDED" -> "已退款";
			default -> status;
		};
	}
}
