package com.luckybox.admin.approval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminApprovalRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminApprovalRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminApprovalRequestResponse> findRequests(String status, String type) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND r.status = ?\n");
			params.add(status);
		}
		if (type != null) {
			where.append("AND r.type = ?\n");
			params.add(type);
		}
		return jdbcTemplate.query(selectSql() + where + """
				ORDER BY r.id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapResponse(rs), params.toArray());
	}

	AdminApprovalRequestResponse findResponse(long requestId) {
		return jdbcTemplate.query(selectSql() + """
				WHERE r.id = ?
				""", (rs, rowNum) -> mapResponse(rs), requestId).stream().findFirst().orElse(null);
	}

	ApprovalRequestRow findRow(long requestId) {
		return jdbcTemplate.query("""
				SELECT id, type, status, entity_type, entity_id, payload_json, reason, requested_by
				FROM admin_approval_requests
				WHERE id = ?
				""", (rs, rowNum) -> new ApprovalRequestRow(
				rs.getLong("id"),
				rs.getString("type"),
				rs.getString("status"),
				rs.getString("entity_type"),
				rs.getString("entity_id"),
				rs.getString("payload_json"),
				rs.getString("reason"),
				rs.getLong("requested_by")), requestId).stream().findFirst().orElse(null);
	}

	long createRequest(String type, String entityType, String entityId, String payloadJson, String reason, long requestedBy) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO admin_approval_requests (
					type, status, entity_type, entity_id, payload_json, reason, requested_by, created_at, updated_at
				)
				VALUES (?, 'PENDING', ?, ?, ?, ?, ?, ?, ?)
				""", type, entityType, entityId, payloadJson, reason, requestedBy, now, now);
		Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return id == null ? 0 : id;
	}

	boolean markApproved(long requestId, long reviewedBy, String resultEntityType, String resultEntityId) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE admin_approval_requests
				SET status = 'APPROVED',
					reviewed_by = ?,
					reviewed_at = ?,
					updated_at = ?,
					result_entity_type = ?,
					result_entity_id = ?
				WHERE id = ? AND status = 'PENDING'
				""", reviewedBy, now, now, resultEntityType, resultEntityId, requestId) > 0;
	}

	boolean markRejected(long requestId, long reviewedBy, String reason) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE admin_approval_requests
				SET status = 'REJECTED',
					reviewed_by = ?,
					reviewed_at = ?,
					updated_at = ?,
					reason = CASE WHEN ? IS NULL THEN reason ELSE reason || char(10) || '駁回原因：' || ? END
				WHERE id = ? AND status = 'PENDING'
				""", reviewedBy, now, now, reason, reason, requestId) > 0;
	}

	private static String selectSql() {
		return """
				SELECT
					r.id, r.type, r.status, r.entity_type, r.entity_id, r.reason, r.payload_json,
					r.requested_by, requester.display_name AS requested_by_display_name,
					r.reviewed_by, reviewer.display_name AS reviewed_by_display_name,
					r.result_entity_type, r.result_entity_id,
					r.created_at, r.reviewed_at, r.updated_at
				FROM admin_approval_requests r
				JOIN users requester ON requester.id = r.requested_by
				LEFT JOIN users reviewer ON reviewer.id = r.reviewed_by
				""";
	}

	private static AdminApprovalRequestResponse mapResponse(ResultSet rs) throws SQLException {
		String type = rs.getString("type");
		String status = rs.getString("status");
		Long reviewedBy = rs.getObject("reviewed_by") == null ? null : rs.getLong("reviewed_by");
		return new AdminApprovalRequestResponse(
				rs.getLong("id"),
				type,
				typeLabel(type),
				status,
				statusLabel(status),
				rs.getString("entity_type"),
				rs.getString("entity_id"),
				rs.getString("reason"),
				rs.getString("payload_json"),
				rs.getLong("requested_by"),
				rs.getString("requested_by_display_name"),
				reviewedBy,
				rs.getString("reviewed_by_display_name"),
				rs.getString("result_entity_type"),
				rs.getString("result_entity_id"),
				rs.getString("created_at"),
				rs.getString("reviewed_at"),
				rs.getString("updated_at"));
	}

	static String typeLabel(String type) {
		return switch (type) {
			case "WALLET_ADJUSTMENT" -> "點數調整";
			case "PAYMENT_REFUND" -> "退款";
			case "COMPENSATION" -> "客服補償";
			default -> type;
		};
	}

	static String statusLabel(String status) {
		return switch (status) {
			case "PENDING" -> "待審核";
			case "APPROVED" -> "已核准";
			case "REJECTED" -> "已駁回";
			default -> status;
		};
	}

	record ApprovalRequestRow(
			long id,
			String type,
			String status,
			String entityType,
			String entityId,
			String payloadJson,
			String reason,
			long requestedBy) {
	}
}
