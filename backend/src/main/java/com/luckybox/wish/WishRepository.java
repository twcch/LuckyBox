package com.luckybox.wish;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class WishRepository {

	private final JdbcTemplate jdbcTemplate;

	WishRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	long createWish(long userId, String content, String status) {
		String now = Instant.now().toString();
		String moderatedAt = "PENDING".equals(status) ? null : now;
		jdbcTemplate.update("""
				INSERT INTO wishes (user_id, content, status, moderated_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", userId, content, status, moderatedAt, now, now);
		Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return id == null ? 0 : id;
	}

	List<WishResponse> publicWishes(int limit) {
		return jdbcTemplate.query("""
				SELECT w.id, w.content, u.display_name, w.status, w.created_at
				FROM wishes w
				JOIN users u ON u.id = w.user_id
				WHERE w.status = 'APPROVED'
				ORDER BY w.id DESC
				LIMIT ?
				""", (rs, rowNum) -> new WishResponse(
				rs.getLong("id"),
				rs.getString("content"),
				maskDisplayName(rs.getString("display_name")),
				rs.getString("status"),
				rs.getString("created_at")), limit);
	}

	List<WishResponse> wishesByUser(long userId, int limit) {
		return jdbcTemplate.query("""
				SELECT w.id, w.content, u.display_name, w.status, w.created_at
				FROM wishes w
				JOIN users u ON u.id = w.user_id
				WHERE w.user_id = ?
				ORDER BY w.id DESC
				LIMIT ?
				""", (rs, rowNum) -> new WishResponse(
				rs.getLong("id"),
				rs.getString("content"),
				rs.getString("display_name"),
				rs.getString("status"),
				rs.getString("created_at")), userId, limit);
	}

	int countByUserToday(long userId, String dayPrefix) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM wishes
				WHERE user_id = ? AND substr(created_at, 1, 10) = ?
				""", Integer.class, userId, dayPrefix);
		return count == null ? 0 : count;
	}

	List<AdminWishResponse> adminWishes(String status, int limit) {
		if (status == null) {
			return jdbcTemplate.query("""
					SELECT w.id, w.content, u.display_name, u.email, w.status,
						w.moderator_note, w.moderated_at, w.created_at
					FROM wishes w
					JOIN users u ON u.id = w.user_id
					ORDER BY w.id DESC
					LIMIT ?
					""", WishRepository::mapAdminWish, limit);
		}
		return jdbcTemplate.query("""
				SELECT w.id, w.content, u.display_name, u.email, w.status,
					w.moderator_note, w.moderated_at, w.created_at
				FROM wishes w
				JOIN users u ON u.id = w.user_id
				WHERE w.status = ?
				ORDER BY w.id DESC
				LIMIT ?
				""", WishRepository::mapAdminWish, status, limit);
	}

	Optional<AdminWishResponse> findAdminWish(long wishId) {
		List<AdminWishResponse> rows = jdbcTemplate.query("""
				SELECT w.id, w.content, u.display_name, u.email, w.status,
					w.moderator_note, w.moderated_at, w.created_at
				FROM wishes w
				JOIN users u ON u.id = w.user_id
				WHERE w.id = ?
				""", WishRepository::mapAdminWish, wishId);
		return rows.stream().findFirst();
	}

	boolean moderateWish(long wishId, String status, String note, long moderatorId) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE wishes
				SET status = ?, moderator_note = ?, moderated_by = ?, moderated_at = ?, updated_at = ?
				WHERE id = ?
				""", status, note, moderatorId, now, now, wishId);
		return rows > 0;
	}

	private static AdminWishResponse mapAdminWish(ResultSet rs, int rowNum) throws SQLException {
		return new AdminWishResponse(
				rs.getLong("id"),
				rs.getString("content"),
				rs.getString("display_name"),
				rs.getString("email"),
				rs.getString("status"),
				rs.getString("moderator_note"),
				rs.getString("moderated_at"),
				rs.getString("created_at"));
	}

	private static String maskDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return "玩**";
		}
		String normalized = displayName.trim();
		int visibleCodePoints = normalized.codePointCount(0, normalized.length()) >= 2 ? 2 : 1;
		int endIndex = normalized.offsetByCodePoints(0, visibleCodePoints);
		return normalized.substring(0, endIndex) + "**";
	}
}
