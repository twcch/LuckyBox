package com.luckybox.prizebox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PrizeBoxRepository {

	private final JdbcTemplate jdbcTemplate;

	PrizeBoxRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<PrizeBoxItemResponse> findPrizes(long userId, String status, String campaignSlug) {
		List<Object> params = new ArrayList<>();
		params.add(userId);
		StringBuilder where = new StringBuilder("WHERE up.user_id = ?\n");
		if (status != null) {
			where.append("AND up.status = ?\n");
			params.add(status);
		}
		if (campaignSlug != null) {
			where.append("AND c.slug = ?\n");
			params.add(campaignSlug);
		}
		return jdbcTemplate.query("""
				SELECT
					up.id, c.slug, c.title, p.id AS prize_id, p.rank, p.name, p.description,
					t.serial_number, up.status, up.shipment_id, up.created_at
				FROM user_prizes up
				JOIN kuji_campaigns c ON c.id = up.campaign_id
				JOIN prizes p ON p.id = up.prize_id
				LEFT JOIN draw_results dr ON dr.id = up.draw_result_id
				LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id
				""" + where + """
				ORDER BY up.id DESC
				""", (rs, rowNum) -> mapPrize(rs), params.toArray());
	}

	List<PrizeBoxCampaignOption> campaignOptions(long userId) {
		return jdbcTemplate.query("""
				SELECT c.slug, c.title, COUNT(*) AS item_count
				FROM user_prizes up
				JOIN kuji_campaigns c ON c.id = up.campaign_id
				WHERE up.user_id = ?
				GROUP BY c.id
				ORDER BY MAX(up.id) DESC
				""", (rs, rowNum) -> new PrizeBoxCampaignOption(
				rs.getString("slug"),
				rs.getString("title"),
				rs.getInt("item_count")), userId);
	}

	Map<String, Integer> statusCounts(long userId) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		jdbcTemplate.query("""
				SELECT status, COUNT(*) AS item_count
				FROM user_prizes
				WHERE user_id = ?
				GROUP BY status
				ORDER BY status
				""", rs -> {
			counts.put(rs.getString("status"), rs.getInt("item_count"));
		}, userId);
		return counts;
	}

	AddressRow findAddress(long userId, long addressId) {
		List<AddressRow> addresses = jdbcTemplate.query("""
				SELECT id, recipient_name, phone, postal_code, city, district, address_line
				FROM user_addresses
				WHERE user_id = ? AND id = ?
				""", (rs, rowNum) -> new AddressRow(
				rs.getLong("id"),
				rs.getString("recipient_name"),
				rs.getString("phone"),
				rs.getString("postal_code"),
				rs.getString("city"),
				rs.getString("district"),
				rs.getString("address_line")), userId, addressId);
		return addresses.stream().findFirst().orElse(null);
	}

	List<PrizeBoxItemResponse> findShippablePrizes(long userId, List<Long> prizeIds) {
		String placeholders = String.join(",", java.util.Collections.nCopies(prizeIds.size(), "?"));
		List<Object> params = new ArrayList<>();
		params.add(userId);
		params.addAll(prizeIds);
		return jdbcTemplate.query("""
				SELECT
					up.id, c.slug, c.title, p.id AS prize_id, p.rank, p.name, p.description,
					t.serial_number, up.status, up.shipment_id, up.created_at
				FROM user_prizes up
				JOIN kuji_campaigns c ON c.id = up.campaign_id
				JOIN prizes p ON p.id = up.prize_id
				LEFT JOIN draw_results dr ON dr.id = up.draw_result_id
				LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id
				WHERE up.user_id = ?
				  AND up.status = 'IN_BOX'
				  AND up.shipment_id IS NULL
				  AND up.id IN (
				""" + placeholders + """
				  )
				ORDER BY up.id DESC
				""", (rs, rowNum) -> mapPrize(rs), params.toArray());
	}

	long createShipment(long userId, AddressRow address, int itemCount, int shippingFee) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO shipments (
					user_id, status, recipient_snapshot, shipping_fee, tracking_number, carrier, admin_note,
					requested_at, shipped_at, delivered_at, created_at, updated_at
				)
				VALUES (?, 'REQUESTED', ?, ?, NULL, NULL, NULL, ?, NULL, NULL, ?, ?)
				""", userId, address.toSnapshotJson(), shippingFee, now, now, now);
		Long shipmentId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return shipmentId == null ? 0 : shipmentId;
	}

	int attachPrizesToShipment(long userId, List<Long> prizeIds, long shipmentId) {
		String placeholders = String.join(",", java.util.Collections.nCopies(prizeIds.size(), "?"));
		List<Object> params = new ArrayList<>();
		params.add(shipmentId);
		params.add(Instant.now().toString());
		params.add(userId);
		params.addAll(prizeIds);
		return jdbcTemplate.update("""
				UPDATE user_prizes
				SET status = 'SHIPMENT_REQUESTED', shipment_id = ?, updated_at = ?
				WHERE user_id = ?
				  AND status = 'IN_BOX'
				  AND shipment_id IS NULL
				  AND id IN (
				""" + placeholders + """
				  )
				""", params.toArray());
	}

	Optional<CouponForShipment> findCoupon(long couponId) {
		List<CouponForShipment> coupons = jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, usage_limit, used_count, starts_at, ends_at, status
				FROM coupons
				WHERE id = ?
				""", (rs, rowNum) -> {
			Integer usageLimit = rs.getObject("usage_limit") == null ? null : rs.getInt("usage_limit");
			return new CouponForShipment(
					rs.getLong("id"),
					rs.getString("code"),
					rs.getString("type"),
					rs.getString("vip_tier"),
					rs.getInt("value"),
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

	void createFreeShippingUsage(long userId, long couponId, long shipmentId, int discountAmount) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO coupon_usages (
					coupon_id, user_id, reference_type, reference_id, discount_amount, point_amount, status, used_at
				)
				VALUES (?, ?, 'Shipment', ?, ?, 0, 'APPLIED', ?)
				""", couponId, userId, shipmentId, discountAmount, now);
	}

	List<ShipmentResponse> findShipments(long userId) {
		List<ShipmentRow> shipments = jdbcTemplate.query("""
				SELECT id, status, recipient_snapshot, shipping_fee, carrier, tracking_number,
					requested_at, shipped_at, delivered_at
				FROM shipments
				WHERE user_id = ?
				ORDER BY id DESC
				""", (rs, rowNum) -> mapShipmentRow(rs), userId);
		if (shipments.isEmpty()) {
			return List.of();
		}
		List<Long> shipmentIds = shipments.stream().map(ShipmentRow::id).toList();
		Map<Long, List<PrizeBoxItemResponse>> prizesByShipment = findPrizesByShipments(userId, shipmentIds);
		return shipments.stream()
				.map(shipment -> shipment.toResponse(prizesByShipment.getOrDefault(shipment.id(), List.of())))
				.toList();
	}

	Optional<ShipmentResponse> findShipment(long userId, long shipmentId) {
		List<ShipmentRow> shipments = jdbcTemplate.query("""
				SELECT id, status, recipient_snapshot, shipping_fee, carrier, tracking_number,
					requested_at, shipped_at, delivered_at
				FROM shipments
				WHERE user_id = ? AND id = ?
				""", (rs, rowNum) -> mapShipmentRow(rs), userId, shipmentId);
		return shipments.stream()
				.findFirst()
				.map(shipment -> shipment.toResponse(findPrizesByShipment(userId, shipment.id())));
	}

	private List<PrizeBoxItemResponse> findPrizesByShipment(long userId, long shipmentId) {
		return jdbcTemplate.query("""
				SELECT
					up.id, c.slug, c.title, p.id AS prize_id, p.rank, p.name, p.description,
					t.serial_number, up.status, up.shipment_id, up.created_at
				FROM user_prizes up
				JOIN kuji_campaigns c ON c.id = up.campaign_id
				JOIN prizes p ON p.id = up.prize_id
				LEFT JOIN draw_results dr ON dr.id = up.draw_result_id
				LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id
				WHERE up.user_id = ? AND up.shipment_id = ?
				ORDER BY up.id DESC
				""", (rs, rowNum) -> mapPrize(rs), userId, shipmentId);
	}

	private Map<Long, List<PrizeBoxItemResponse>> findPrizesByShipments(long userId, List<Long> shipmentIds) {
		String placeholders = shipmentIds.stream().map(id -> "?").collect(Collectors.joining(", "));
		String sql = "SELECT up.id, c.slug, c.title, p.id AS prize_id, p.rank, p.name, p.description, "
				+ "t.serial_number, up.status, up.shipment_id, up.created_at "
				+ "FROM user_prizes up "
				+ "JOIN kuji_campaigns c ON c.id = up.campaign_id "
				+ "JOIN prizes p ON p.id = up.prize_id "
				+ "LEFT JOIN draw_results dr ON dr.id = up.draw_result_id "
				+ "LEFT JOIN kuji_tickets t ON t.id = dr.ticket_id "
				+ "WHERE up.user_id = ? AND up.shipment_id IN (" + placeholders + ") "
				+ "ORDER BY up.id DESC";
		Object[] params = new Object[shipmentIds.size() + 1];
		params[0] = userId;
		for (int index = 0; index < shipmentIds.size(); index++) {
			params[index + 1] = shipmentIds.get(index);
		}
		Map<Long, List<PrizeBoxItemResponse>> grouped = new LinkedHashMap<>();
		jdbcTemplate.query(sql, rs -> {
			long shipmentId = rs.getLong("shipment_id");
			grouped.computeIfAbsent(shipmentId, key -> new ArrayList<>()).add(mapPrize(rs));
		}, params);
		return grouped;
	}

	private static PrizeBoxItemResponse mapPrize(ResultSet rs) throws SQLException {
		Long shipmentId = rs.getObject("shipment_id") == null ? null : rs.getLong("shipment_id");
		return new PrizeBoxItemResponse(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				rs.getLong("prize_id"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getString("serial_number"),
				rs.getString("status"),
				shipmentId,
				rs.getString("created_at"));
	}

	private static ShipmentRow mapShipmentRow(ResultSet rs) throws SQLException {
		String snapshot = rs.getString("recipient_snapshot");
		return new ShipmentRow(
				rs.getLong("id"),
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

	record AddressRow(
			long id,
			String recipientName,
			String phone,
			String postalCode,
			String city,
			String district,
			String addressLine) {

		String toSnapshotJson() {
			return """
					{"recipientName":"%s","phone":"%s","postalCode":"%s","city":"%s","district":"%s","addressLine":"%s"}
					""".formatted(
					escape(recipientName),
					escape(phone),
					escape(postalCode),
					escape(city),
					escape(district),
					escape(addressLine)).trim();
		}

		private static String escape(String value) {
			if (value == null) {
				return "";
			}
			return value.replace("\\", "\\\\").replace("\"", "\\\"");
		}
	}

	record CouponForShipment(
			long id,
			String code,
			String type,
			String vipTier,
			int value,
			Integer usageLimit,
			int usedCount,
			String startsAt,
			String endsAt,
			String status) {
	}

	private record ShipmentRow(
			long id,
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
			String requestedAt,
			String shippedAt,
			String deliveredAt) {

		ShipmentResponse toResponse(List<PrizeBoxItemResponse> items) {
			return new ShipmentResponse(
					id,
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
					requestedAt,
					shippedAt,
					deliveredAt,
					items);
		}
	}
}
