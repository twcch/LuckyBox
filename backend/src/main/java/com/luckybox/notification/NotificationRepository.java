package com.luckybox.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {

	private final JdbcTemplate jdbcTemplate;

	NotificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	NotificationOverviewResponse overview(long userId) {
		return new NotificationOverviewResponse(unreadCount(userId), findNotifications(userId));
	}

	boolean createShipmentNotification(
			long userId,
			String type,
			String title,
			String body,
			String linkUrl,
			long shipmentId) {
		return jdbcTemplate.update("""
				INSERT OR IGNORE INTO user_notifications (
					user_id, type, title, body, link_url, reference_type, reference_id, read_at, created_at
				)
				VALUES (?, ?, ?, ?, ?, 'Shipment', ?, NULL, ?)
				""", userId, type, title, body, linkUrl, shipmentId, Instant.now().toString()) > 0;
	}

	Optional<String> findUserEmail(long userId) {
		return jdbcTemplate.query("""
				SELECT email
				FROM users
				WHERE id = ?
				""", (rs, rowNum) -> rs.getString("email"), userId)
				.stream()
				.filter(email -> email != null && !email.isBlank())
				.findFirst();
	}

	void createNotification(
			long userId,
			String type,
			String title,
			String body,
			String linkUrl,
			String referenceType,
			Long referenceId) {
		jdbcTemplate.update("""
				INSERT INTO user_notifications (
					user_id, type, title, body, link_url, reference_type, reference_id, read_at, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)
				""", userId, type, title, body, linkUrl, referenceType, referenceId, Instant.now().toString());
	}

	NotificationResponse markRead(long userId, long notificationId) {
		jdbcTemplate.update("""
				UPDATE user_notifications
				SET read_at = COALESCE(read_at, ?)
				WHERE user_id = ? AND id = ?
				""", Instant.now().toString(), userId, notificationId);
		return findNotification(userId, notificationId);
	}

	NotificationResponse findNotification(long userId, long notificationId) {
		List<NotificationResponse> notifications = jdbcTemplate.query("""
				SELECT id, type, title, body, link_url, reference_type, reference_id, read_at, created_at
				FROM user_notifications
				WHERE user_id = ? AND id = ?
				""", (rs, rowNum) -> mapNotification(rs), userId, notificationId);
		return notifications.stream().findFirst().orElse(null);
	}

	private List<NotificationResponse> findNotifications(long userId) {
		return jdbcTemplate.query("""
				SELECT id, type, title, body, link_url, reference_type, reference_id, read_at, created_at
				FROM user_notifications
				WHERE user_id = ?
				ORDER BY read_at IS NOT NULL, created_at DESC, id DESC
				LIMIT 20
				""", (rs, rowNum) -> mapNotification(rs), userId);
	}

	private int unreadCount(long userId) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM user_notifications
				WHERE user_id = ? AND read_at IS NULL
				""", Integer.class, userId);
		return count == null ? 0 : count;
	}

	private static NotificationResponse mapNotification(ResultSet rs) throws SQLException {
		long referenceId = rs.getLong("reference_id");
		boolean hasReferenceId = !rs.wasNull();
		return new NotificationResponse(
				rs.getLong("id"),
				rs.getString("type"),
				rs.getString("title"),
				rs.getString("body"),
				rs.getString("link_url"),
				rs.getString("reference_type"),
				hasReferenceId ? referenceId : null,
				rs.getString("read_at"),
				rs.getString("created_at"));
	}
}
