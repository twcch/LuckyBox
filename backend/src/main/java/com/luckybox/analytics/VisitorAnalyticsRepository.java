package com.luckybox.analytics;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class VisitorAnalyticsRepository {

	private final JdbcTemplate jdbcTemplate;

	VisitorAnalyticsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void upsertVisit(String visitorId, String path, String now) {
		jdbcTemplate.update("""
				INSERT INTO visitor_sessions (
					visitor_id, first_path, last_path, visit_count, first_seen_at, last_seen_at
				)
				VALUES (?, ?, ?, 1, ?, ?)
				ON CONFLICT(visitor_id) DO UPDATE SET
					last_path = excluded.last_path,
					last_seen_at = excluded.last_seen_at,
					visit_count = visitor_sessions.visit_count + 1
				""", visitorId, path, path, now, now);
	}

	void linkRegistration(String visitorId, long userId, String now) {
		jdbcTemplate.update("""
				INSERT INTO visitor_sessions (
					visitor_id, first_path, last_path, visit_count,
					registered_user_id, first_seen_at, last_seen_at, registered_at
				)
				VALUES (?, '/register', '/register', 0, ?, ?, ?, ?)
				ON CONFLICT(visitor_id) DO UPDATE SET
					registered_user_id = COALESCE(visitor_sessions.registered_user_id, excluded.registered_user_id),
					registered_at = COALESCE(visitor_sessions.registered_at, excluded.registered_at),
					last_seen_at = excluded.last_seen_at
				""", visitorId, userId, now, now, now);
	}

	Optional<VisitorVisitResponse> findVisit(String visitorId) {
		return jdbcTemplate.query("""
				SELECT visitor_id, visit_count, registered_user_id IS NOT NULL AS registered
				FROM visitor_sessions
				WHERE visitor_id = ?
				""", rs -> {
			if (!rs.next()) {
				return Optional.empty();
			}
			return Optional.of(new VisitorVisitResponse(
					rs.getString("visitor_id"),
					rs.getInt("visit_count"),
					rs.getBoolean("registered")));
		}, visitorId);
	}
}
