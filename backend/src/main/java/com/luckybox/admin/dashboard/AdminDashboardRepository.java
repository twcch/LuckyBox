package com.luckybox.admin.dashboard;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.luckybox.common.PiiMasking;

@Repository
class AdminDashboardRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminDashboardRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	int todayTopUpAmount(String since) {
		return intValue("""
				SELECT COALESCE(SUM(amount), 0)
				FROM payment_orders
				WHERE status = 'PAID' AND paid_at >= ?
				""", since);
	}

	int todayDrawCount(String since) {
		return intValue("""
				SELECT COALESCE(SUM(quantity), 0)
				FROM draw_orders
				WHERE status = 'COMPLETED' AND completed_at >= ?
				""", since);
	}

	int todayNewUsers(String since) {
		return intValue("""
				SELECT COUNT(*)
				FROM users
				WHERE created_at >= ?
				""", since);
	}

	int todayActiveUsers(String since) {
		return intValue("""
				SELECT COUNT(*)
				FROM (
					SELECT user_id
					FROM draw_orders
					WHERE status = 'COMPLETED' AND completed_at >= ?
					UNION
					SELECT user_id
					FROM payment_orders
					WHERE status = 'PAID' AND paid_at >= ?
				) active_users
				""", since, since);
	}

	int liveCampaigns() {
		return intValue("""
				SELECT COUNT(*)
				FROM kuji_campaigns
				WHERE status = 'LIVE'
				""");
	}

	int nearSoldCampaigns() {
		return intValue("""
				SELECT COUNT(*)
				FROM kuji_campaigns
				WHERE status = 'LIVE'
				  AND total_tickets > 0
				  AND remaining_tickets > 0
				  AND (remaining_tickets <= 10 OR remaining_tickets * 100 <= total_tickets * 10)
				""");
	}

	int requestedShipments() {
		return intValue("""
				SELECT COUNT(*)
				FROM shipments
				WHERE status = 'REQUESTED'
				""");
	}

	int failedPayments() {
		return intValue("""
				SELECT COUNT(*)
				FROM payment_orders
				WHERE status = 'FAILED'
				""");
	}

	int supportQueue() {
		return intValue("""
				SELECT
					(SELECT COUNT(*) FROM wishes WHERE status = 'PENDING')
					+
					(SELECT COUNT(*) FROM payment_orders WHERE status = 'FAILED')
				""");
	}

	int todayAuditLogs(String since) {
		return intValue("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE created_at >= ?
				""", since);
	}

	int todayFailedDrawOrders(String since) {
		return intValue("""
				SELECT COUNT(*)
				FROM draw_orders
				WHERE status = 'FAILED' AND created_at >= ?
				""", since);
	}

	int dataConsistencyFindings() {
		return intValue("""
				SELECT
					(
						SELECT COUNT(*)
						FROM kuji_campaigns c
						WHERE (SELECT COUNT(*) FROM kuji_tickets t WHERE t.campaign_id = c.id) > 0
						  AND c.remaining_tickets <> (
								SELECT COUNT(*)
								FROM kuji_tickets t2
								WHERE t2.campaign_id = c.id AND t2.status = 'AVAILABLE')
					)
					+
					(
						SELECT COUNT(*)
						FROM kuji_campaigns c
						WHERE (SELECT COUNT(*) FROM kuji_tickets t WHERE t.campaign_id = c.id AND t.status = 'DRAWN')
							<> (SELECT COUNT(*) FROM draw_results r WHERE r.campaign_id = c.id)
					)
					+
					(
						SELECT COUNT(*)
						FROM kuji_campaigns
						WHERE status = 'SOLD_OUT' AND fairness_mode = 'HASH_COMMIT_REVEAL'
						  AND server_seed IS NOT NULL AND revealed_seed IS NULL
					)
					+
					(
						SELECT COUNT(*)
						FROM prizes p
						WHERE p.is_last_prize = 0
						  AND (SELECT COUNT(*) FROM kuji_tickets t WHERE t.prize_id = p.id) > 0
						  AND p.remaining_quantity <> (
								SELECT COUNT(*)
								FROM kuji_tickets t2
								WHERE t2.prize_id = p.id AND t2.status = 'AVAILABLE')
					)
				""");
	}

	int totalUsers() {
		return intValue("""
				SELECT COUNT(*)
				FROM users
				WHERE role = 'USER'
				""");
	}

	int visitorCount() {
		return intValue("""
				SELECT COUNT(*)
				FROM visitor_sessions
				""");
	}

	int registeredVisitorCount() {
		return intValue("""
				SELECT COUNT(*)
				FROM visitor_sessions
				WHERE registered_user_id IS NOT NULL
				""");
	}

	int paidTopUpUsers() {
		return intValue("""
				SELECT COUNT(DISTINCT po.user_id)
				FROM payment_orders po
				JOIN users u ON u.id = po.user_id
				WHERE po.status IN ('PAID', 'REFUNDED')
				  AND u.role = 'USER'
				""");
	}

	int completedDrawUsers() {
		return intValue("""
				SELECT COUNT(DISTINCT d.user_id)
				FROM draw_orders d
				JOIN users u ON u.id = d.user_id
				WHERE d.status = 'COMPLETED'
				  AND u.role = 'USER'
				""");
	}

	int totalPaidTopUpAmount() {
		return intValue("""
				SELECT COALESCE(SUM(po.amount), 0)
				FROM payment_orders po
				JOIN users u ON u.id = po.user_id
				WHERE po.status IN ('PAID', 'REFUNDED')
				  AND u.role = 'USER'
				""");
	}

	int totalCompletedDrawQuantity() {
		return intValue("""
				SELECT COALESCE(SUM(d.quantity), 0)
				FROM draw_orders d
				JOIN users u ON u.id = d.user_id
				WHERE d.status = 'COMPLETED'
				  AND u.role = 'USER'
				""");
	}

	Double averageSoldOutHours() {
		return nullableDoubleValue("""
				SELECT AVG((julianday(COALESCE(updated_at, created_at)) - julianday(COALESCE(sales_start_at, created_at))) * 24.0)
				FROM kuji_campaigns
				WHERE total_tickets > 0
				  AND remaining_tickets = 0
				  AND status IN ('SOLD_OUT', 'ENDED')
				""");
	}

	int totalUserPrizes() {
		return intValue("""
				SELECT COUNT(*)
				FROM user_prizes
				""");
	}

	int shipmentRequestedPrizes() {
		return intValue("""
				SELECT COUNT(*)
				FROM user_prizes
				WHERE shipment_id IS NOT NULL
				""");
	}

	int todaySupportCases(String since) {
		return intValue("""
				SELECT COUNT(*)
				FROM audit_logs
				WHERE created_at >= ?
				  AND action IN (
					'ADMIN_MEMBER_NOTE_ADDED',
					'ADMIN_COMPENSATION_GRANTED',
					'ADMIN_PAYMENT_REFUNDED',
					'ADMIN_SHIPMENT_RESOLVED'
				  )
				""", since);
	}

	int refundOrCompensationCases() {
		return intValue("""
				SELECT
					(SELECT COUNT(*) FROM payment_orders WHERE status = 'REFUNDED')
					+
					(SELECT COUNT(*) FROM wallet_ledger WHERE type = 'COMPENSATION')
				""");
	}

	int successfulPaymentOrders() {
		return intValue("""
				SELECT COUNT(*)
				FROM payment_orders
				WHERE status IN ('PAID', 'REFUNDED')
				""");
	}

	int paymentOrderCount() {
		return intValue("""
				SELECT COUNT(*)
				FROM payment_orders
				""");
	}

	int failedPaymentOrderCount() {
		return intValue("""
				SELECT COUNT(*)
				FROM payment_orders
				WHERE status = 'FAILED'
				""");
	}

	int drawOrderCount() {
		return intValue("""
				SELECT COUNT(*)
				FROM draw_orders
				""");
	}

	int failedDrawOrderCount() {
		return intValue("""
				SELECT COUNT(*)
				FROM draw_orders
				WHERE status = 'FAILED'
				""");
	}

	List<Double> completedDrawLatenciesMillis() {
		return jdbcTemplate.queryForList("""
				SELECT (julianday(completed_at) - julianday(created_at)) * 86400000.0 AS latency_ms
				FROM draw_orders
				WHERE status = 'COMPLETED' AND completed_at IS NOT NULL
				ORDER BY latency_ms
				""", Double.class);
	}

	List<AdminDashboardShipmentResponse> latestRequestedShipments() {
		return jdbcTemplate.query("""
				SELECT s.id, u.display_name, u.email, COUNT(up.id) AS item_count, s.requested_at
				FROM shipments s
				JOIN users u ON u.id = s.user_id
				LEFT JOIN user_prizes up ON up.shipment_id = s.id
				WHERE s.status = 'REQUESTED'
				GROUP BY s.id, u.display_name, u.email, s.requested_at
				ORDER BY s.id DESC
				LIMIT 5
				""", (rs, rowNum) -> new AdminDashboardShipmentResponse(
				rs.getLong("id"),
				rs.getString("display_name"),
				PiiMasking.maskEmail(rs.getString("email")),
				rs.getInt("item_count"),
				rs.getString("requested_at")));
	}

	List<AdminDashboardActivityResponse> recentActivities() {
		return jdbcTemplate.query("""
				SELECT id, actor_role, action, entity_type, entity_id, created_at
				FROM audit_logs
				ORDER BY id DESC
				LIMIT 8
				""", (rs, rowNum) -> new AdminDashboardActivityResponse(
				rs.getLong("id"),
				rs.getString("actor_role"),
				rs.getString("action"),
				rs.getString("entity_type"),
				rs.getString("entity_id"),
				rs.getString("created_at")));
	}

	private int intValue(String sql, Object... params) {
		Number value = jdbcTemplate.queryForObject(sql, Number.class, params);
		return value == null ? 0 : value.intValue();
	}

	private Double nullableDoubleValue(String sql, Object... params) {
		Number value = jdbcTemplate.queryForObject(sql, Number.class, params);
		return value == null ? null : value.doubleValue();
	}
}
