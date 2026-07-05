package com.luckybox.admin.shipment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.luckybox.common.PiiMasking;

@Repository
class AdminShipmentRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminShipmentRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminShipmentResponse> findShipments(String status) {
		String where = status == null ? "" : "WHERE s.status = ?\n";
		Object[] params = status == null ? new Object[] {} : new Object[] { status };
		List<ShipmentRow> shipments = jdbcTemplate.query("""
				SELECT
					s.id, s.user_id, u.email, u.display_name, s.status, s.recipient_snapshot,
					s.shipping_fee, s.carrier, s.tracking_number, s.admin_note,
					s.requested_at, s.shipped_at, s.delivered_at
				FROM shipments s
				JOIN users u ON u.id = s.user_id
				""" + where + """
				ORDER BY s.id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapShipmentRow(rs), params);
		if (shipments.isEmpty()) {
			return List.of();
		}
		List<Long> shipmentIds = shipments.stream().map(ShipmentRow::id).toList();
		Map<Long, List<AdminShipmentItemResponse>> itemsByShipment = findShipmentItemsByShipments(shipmentIds);
		return shipments.stream()
				.map(shipment -> shipment.toResponse(itemsByShipment.getOrDefault(shipment.id(), List.of())))
				.toList();
	}

	AdminShipmentResponse findShipment(long shipmentId) {
		List<ShipmentRow> shipments = jdbcTemplate.query("""
				SELECT
					s.id, s.user_id, u.email, u.display_name, s.status, s.recipient_snapshot,
					s.shipping_fee, s.carrier, s.tracking_number, s.admin_note,
					s.requested_at, s.shipped_at, s.delivered_at
				FROM shipments s
				JOIN users u ON u.id = s.user_id
				WHERE s.id = ?
				""", (rs, rowNum) -> mapShipmentRow(rs), shipmentId);
		return shipments.stream()
				.findFirst()
				.map(shipment -> shipment.toResponse(findShipmentItems(shipment.id())))
				.orElse(null);
	}

	boolean updateShipment(long shipmentId, String status, String carrier, String trackingNumber, String adminNote) {
		AdminShipmentResponse current = findShipment(shipmentId);
		if (current == null) {
			return false;
		}
		String now = Instant.now().toString();
		String shippedAt = current.shippedAt();
		String deliveredAt = current.deliveredAt();
		if (("SHIPPED".equals(status) || "DELIVERED".equals(status)) && shippedAt == null) {
			shippedAt = now;
		}
		if ("DELIVERED".equals(status) && deliveredAt == null) {
			deliveredAt = now;
		}
		int updated = jdbcTemplate.update("""
				UPDATE shipments
				SET status = ?, carrier = ?, tracking_number = ?, admin_note = ?,
					shipped_at = ?, delivered_at = ?, updated_at = ?
				WHERE id = ?
				""", status, blankToNull(carrier), blankToNull(trackingNumber), blankToNull(adminNote),
				shippedAt, deliveredAt, now, shipmentId);
		if (updated == 0) {
			return false;
		}
		jdbcTemplate.update("""
				UPDATE user_prizes
				SET status = ?, updated_at = ?
				WHERE shipment_id = ?
				""", prizeStatusForShipment(status), now, shipmentId);
		return true;
	}

	/**
	 * 瑕疵/換貨處理：更新出貨單狀態並把原因併入 admin_note；同時更新所屬戰利品狀態。
	 * clearLink=true（退回）會把戰利品 shipment_id 清空使其回到戰利品盒、可重新申請出貨。
	 */
	boolean resolveShipment(
			long shipmentId, String shipmentStatus, String prizeStatus, boolean clearLink, String reason) {
		AdminShipmentResponse current = findShipment(shipmentId);
		if (current == null) {
			return false;
		}
		String now = Instant.now().toString();
		String mergedNote = mergeNote(current.adminNote(), reason);
		int updated = jdbcTemplate.update("""
				UPDATE shipments
				SET status = ?, admin_note = ?, updated_at = ?
				WHERE id = ?
				""", shipmentStatus, mergedNote, now, shipmentId);
		if (updated == 0) {
			return false;
		}
		if (clearLink) {
			jdbcTemplate.update("""
					UPDATE user_prizes
					SET status = ?, shipment_id = NULL, updated_at = ?
					WHERE shipment_id = ?
					""", prizeStatus, now, shipmentId);
		}
		else {
			jdbcTemplate.update("""
					UPDATE user_prizes
					SET status = ?, updated_at = ?
					WHERE shipment_id = ?
					""", prizeStatus, now, shipmentId);
		}
		return true;
	}

	private static String mergeNote(String existingNote, String reason) {
		String entry = "[" + Instant.now().toString().substring(0, 10) + "] " + reason;
		if (existingNote == null || existingNote.isBlank()) {
			return entry;
		}
		return existingNote + "\n" + entry;
	}

	private Map<Long, List<AdminShipmentItemResponse>> findShipmentItemsByShipments(List<Long> shipmentIds) {
		String placeholders = shipmentIds.stream().map(id -> "?").collect(Collectors.joining(", "));
		String sql = "SELECT up.shipment_id, up.id, c.title, p.rank, p.name, t.serial_number, up.status "
				+ "FROM user_prizes up "
				+ "JOIN kuji_campaigns c ON c.id = up.campaign_id "
				+ "JOIN prizes p ON p.id = up.prize_id "
				+ "LEFT JOIN draw_results dr ON dr.id = up.draw_result_id "
				+ "LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id "
				+ "WHERE up.shipment_id IN (" + placeholders + ") "
				+ "ORDER BY up.id DESC";
		Map<Long, List<AdminShipmentItemResponse>> grouped = new LinkedHashMap<>();
		jdbcTemplate.query(sql, rs -> {
			long shipmentId = rs.getLong("shipment_id");
			grouped.computeIfAbsent(shipmentId, key -> new ArrayList<>()).add(new AdminShipmentItemResponse(
					rs.getLong("id"),
					rs.getString("title"),
					rs.getString("rank"),
					rs.getString("name"),
					rs.getString("serial_number"),
					rs.getString("status")));
		}, shipmentIds.toArray());
		return grouped;
	}

	private List<AdminShipmentItemResponse> findShipmentItems(long shipmentId) {
		return jdbcTemplate.query("""
				SELECT up.id, c.title, p.rank, p.name, t.serial_number, up.status
				FROM user_prizes up
				JOIN kuji_campaigns c ON c.id = up.campaign_id
				JOIN prizes p ON p.id = up.prize_id
				LEFT JOIN draw_results dr ON dr.id = up.draw_result_id
				LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id
				WHERE up.shipment_id = ?
				ORDER BY up.id DESC
				""", (rs, rowNum) -> new AdminShipmentItemResponse(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getString("serial_number"),
				rs.getString("status")), shipmentId);
	}

	private static ShipmentRow mapShipmentRow(ResultSet rs) throws SQLException {
		String snapshot = rs.getString("recipient_snapshot");
		return new ShipmentRow(
				rs.getLong("id"),
				rs.getLong("user_id"),
				PiiMasking.maskEmail(rs.getString("email")),
				rs.getString("display_name"),
				rs.getString("status"),
				rs.getInt("shipping_fee"),
				extract(snapshot, "recipientName"),
				extract(snapshot, "phone"),
				extract(snapshot, "postalCode"),
				extract(snapshot, "city"),
				extract(snapshot, "district"),
				extract(snapshot, "addressLine"),
				rs.getString("carrier"),
				rs.getString("tracking_number"),
				rs.getString("admin_note"),
				rs.getString("requested_at"),
				rs.getString("shipped_at"),
				rs.getString("delivered_at"));
	}

	private static String extract(String json, String key) {
		if (json == null) {
			return "";
		}
		String marker = "\"" + key + "\":\"";
		int start = json.indexOf(marker);
		if (start < 0) {
			return "";
		}
		int valueStart = start + marker.length();
		int valueEnd = json.indexOf('"', valueStart);
		return valueEnd < 0 ? "" : json.substring(valueStart, valueEnd);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String prizeStatusForShipment(String shipmentStatus) {
		return switch (shipmentStatus) {
			case "SHIPPED" -> "SHIPPED";
			case "DELIVERED" -> "DELIVERED";
			default -> "SHIPMENT_REQUESTED";
		};
	}

	private record ShipmentRow(
			long id,
			long userId,
			String userEmail,
			String userDisplayName,
			String status,
			int shippingFee,
			String recipientName,
			String phone,
			String postalCode,
			String city,
			String district,
			String addressLine,
			String carrier,
			String trackingNumber,
			String adminNote,
			String requestedAt,
			String shippedAt,
			String deliveredAt) {

		AdminShipmentResponse toResponse(List<AdminShipmentItemResponse> items) {
			return new AdminShipmentResponse(
					id,
					userId,
					userEmail,
					userDisplayName,
					status,
					items.size(),
					shippingFee,
					recipientName,
					phone,
					postalCode,
					city,
					district,
					addressLine,
					carrier,
					trackingNumber,
					adminNote,
					requestedAt,
					shippedAt,
					deliveredAt,
					items);
		}
	}
}
