package com.luckybox.wallet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class WalletRepository {

	private final JdbcTemplate jdbcTemplate;

	WalletRepository(JdbcTemplate jdbcTemplate) {
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

	WalletSummaryResponse walletSummary(long userId) {
		ensureWallet(userId);
		return jdbcTemplate.queryForObject("""
				SELECT cash_point_balance, bonus_point_balance, locked_balance
				FROM wallets
				WHERE user_id = ?
				""", (rs, rowNum) -> mapSummary(rs), userId);
	}

	List<WalletLedgerResponse> ledger(long userId) {
		ensureWallet(userId);
		return jdbcTemplate.query("""
				SELECT id, type, amount, point_kind, balance_after, reference_type, reference_id, reason, created_at
				FROM wallet_ledger
				WHERE user_id = ?
				ORDER BY id DESC
				LIMIT 20
				""", (rs, rowNum) -> mapLedger(rs), userId);
	}

	PaymentOrderRow createPaymentOrder(long userId, TopUpPlanResponse plan, String provider) {
		String now = Instant.now().toString();
		String normalizedProvider = provider == null ? "MOCK" : provider.trim().toUpperCase();
		String merchantTradeNo = merchantTradeNo(normalizedProvider, userId);
		jdbcTemplate.update("""
				INSERT INTO payment_orders (
					user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
					status, provider_payload, paid_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, NULL, ?, ?)
				""",
				userId,
				normalizedProvider,
				merchantTradeNo,
				plan.amount(),
				plan.pointAmount(),
				plan.bonusPointAmount(),
				"{\"planId\":\"" + plan.id() + "\",\"provider\":\"" + normalizedProvider + "\"}",
				now,
				now);
		Long orderId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return findPaymentOrder(userId, orderId == null ? 0 : orderId)
				.orElseThrow(() -> new IllegalStateException("Payment order was not created"));
	}

	private static String merchantTradeNo(String provider, long userId) {
		if ("ECPAY".equals(provider)) {
			String timePart = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
			String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
			return ("LB" + timePart + randomPart).substring(0, Math.min(20, 2 + timePart.length() + randomPart.length()));
		}
		if ("LINEPAY".equals(provider)) {
			return "LINEPAY-" + userId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
		}
		if ("JKOPAY".equals(provider)) {
			return "JKOPAY-" + userId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
		}
		return "MOCK-" + userId + "-" + UUID.randomUUID().toString().substring(0, 12);
	}

	Optional<PaymentOrderRow> findPaymentOrder(long userId, long orderId) {
		List<PaymentOrderRow> orders = jdbcTemplate.query("""
				SELECT id, user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
					status, created_at, paid_at
				FROM payment_orders
				WHERE user_id = ? AND id = ?
				""", (rs, rowNum) -> mapPaymentOrder(rs), userId, orderId);
		return orders.stream().findFirst();
	}

	Optional<PaymentOrderRow> findPaymentOrderByMerchantTradeNo(String provider, String merchantTradeNo) {
		List<PaymentOrderRow> orders = jdbcTemplate.query("""
				SELECT id, user_id, provider, merchant_trade_no, amount, point_amount, bonus_point_amount,
					status, created_at, paid_at
				FROM payment_orders
				WHERE upper(provider) = upper(?) AND merchant_trade_no = ?
				""", (rs, rowNum) -> mapPaymentOrder(rs), provider, merchantTradeNo);
		return orders.stream().findFirst();
	}

	boolean markPaymentOrderPaid(long userId, long orderId) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE payment_orders
				SET status = 'PAID', paid_at = ?, updated_at = ?
				WHERE user_id = ? AND id = ? AND status = 'PENDING'
				""", now, now, userId, orderId);
		return rows > 0;
	}

	boolean markPaymentOrderPaid(String provider, String merchantTradeNo) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE payment_orders
				SET status = 'PAID', paid_at = ?, updated_at = ?
				WHERE upper(provider) = upper(?) AND merchant_trade_no = ? AND status = 'PENDING'
				""", now, now, provider, merchantTradeNo);
		return rows > 0;
	}

	boolean markPaymentOrderTerminal(String provider, String merchantTradeNo, String status) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE payment_orders
				SET status = ?, updated_at = ?
				WHERE upper(provider) = upper(?) AND merchant_trade_no = ? AND status = 'PENDING'
				""", status, now, provider, merchantTradeNo);
		return rows > 0;
	}

	void updateProviderPayload(String provider, String merchantTradeNo, String providerPayload) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE payment_orders
				SET provider_payload = ?, updated_at = ?
				WHERE upper(provider) = upper(?) AND merchant_trade_no = ?
				""", providerPayload, now, provider, merchantTradeNo);
	}

	void addCashPoints(long userId, long walletId, long paymentOrderId, int amount) {
		if (amount <= 0) {
			return;
		}
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE wallets
				SET cash_point_balance = cash_point_balance + ?, updated_at = ?
				WHERE id = ? AND user_id = ?
				""", amount, now, walletId, userId);
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT cash_point_balance FROM wallets WHERE id = ? AND user_id = ?",
				Integer.class,
				walletId,
				userId);
		insertLedger(userId, walletId, "TOP_UP", amount, "CASH", balanceAfter, paymentOrderId, "付款儲值入點", now);
	}

	void addBonusPoints(long userId, long walletId, long paymentOrderId, int amount) {
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
				Integer.class,
				walletId,
				userId);
		insertLedger(userId, walletId, "TOP_UP_BONUS", amount, "BONUS", balanceAfter, paymentOrderId, "付款儲值贈點", now);
	}

	int countPaidPaymentOrders(long userId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM payment_orders WHERE user_id = ? AND status = 'PAID'", Integer.class, userId);
		return count == null ? 0 : count;
	}

	int totalDrawSpend(long userId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(point_spent), 0) FROM draw_orders WHERE user_id = ? AND status = 'COMPLETED'",
				Integer.class, userId);
		return total == null ? 0 : total;
	}

	void addFirstDepositBonus(long userId, long walletId, long paymentOrderId, int amount) {
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
				Integer.class,
				walletId,
				userId);
		insertLedger(userId, walletId, "FIRST_DEPOSIT_BONUS", amount, "BONUS", balanceAfter, paymentOrderId, "首儲贈點", now);
	}

	private void insertLedger(
			long userId,
			long walletId,
			String type,
			int amount,
			String pointKind,
			Integer balanceAfter,
			long paymentOrderId,
			String reason,
			String now) {
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, 'PaymentOrder', ?, ?, ?, ?)
				""",
				userId,
				walletId,
				type,
				amount,
				pointKind,
				balanceAfter == null ? 0 : balanceAfter,
				paymentOrderId,
				reason,
				userId,
				now);
	}

	private Optional<Long> findWalletId(long userId) {
		List<Long> walletIds = jdbcTemplate.query("""
				SELECT id FROM wallets WHERE user_id = ?
				""", (rs, rowNum) -> rs.getLong("id"), userId);
		return walletIds.stream().findFirst();
	}

	private static WalletSummaryResponse mapSummary(ResultSet rs) throws SQLException {
		int cash = rs.getInt("cash_point_balance");
		int bonus = rs.getInt("bonus_point_balance");
		int locked = rs.getInt("locked_balance");
		return new WalletSummaryResponse(cash, bonus, locked, cash + bonus - locked);
	}

	private static WalletLedgerResponse mapLedger(ResultSet rs) throws SQLException {
		Long referenceId = rs.getObject("reference_id") == null ? null : rs.getLong("reference_id");
		return new WalletLedgerResponse(
				rs.getLong("id"),
				rs.getString("type"),
				rs.getInt("amount"),
				rs.getString("point_kind"),
				rs.getInt("balance_after"),
				rs.getString("reference_type"),
				referenceId,
				rs.getString("reason"),
				rs.getString("created_at"));
	}

	private static PaymentOrderRow mapPaymentOrder(ResultSet rs) throws SQLException {
		return new PaymentOrderRow(
				rs.getLong("id"),
				rs.getLong("user_id"),
				rs.getString("provider"),
				rs.getString("merchant_trade_no"),
				rs.getInt("amount"),
				rs.getInt("point_amount"),
				rs.getInt("bonus_point_amount"),
				rs.getString("status"),
				rs.getString("created_at"),
				rs.getString("paid_at"));
	}

	record PaymentOrderRow(
			long id,
			long userId,
			String provider,
			String merchantTradeNo,
			int amount,
			int pointAmount,
			int bonusPointAmount,
			String status,
			String createdAt,
			String paidAt) {

		PaymentOrderResponse toResponse() {
			return new PaymentOrderResponse(
					id,
					provider,
					merchantTradeNo,
					amount,
					pointAmount,
					bonusPointAmount,
					status,
					createdAt,
					paidAt);
		}
	}
}
