package com.luckybox.admin.news;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminNewsRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminNewsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminNewsResponse> findNews(String status, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND n.status = ?\n");
			params.add(status);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(n.title) LIKE ?
						OR lower(n.slug) LIKE ?
						OR lower(n.content) LIKE ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
		}
		return jdbcTemplate.query("""
				SELECT id, title, slug, content, status, published_at, unpublish_at, created_at, updated_at
				FROM news n
				""" + where + """
				ORDER BY id DESC
				LIMIT 200
				""", (rs, rowNum) -> mapNews(rs), params.toArray());
	}

	AdminNewsResponse findNews(long newsId) {
		List<AdminNewsResponse> rows = jdbcTemplate.query("""
				SELECT id, title, slug, content, status, published_at, unpublish_at, created_at, updated_at
				FROM news
				WHERE id = ?
				""", (rs, rowNum) -> mapNews(rs), newsId);
		return rows.stream().findFirst().orElse(null);
	}

	AdminNewsResponse findNewsBySlug(String slug) {
		List<AdminNewsResponse> rows = jdbcTemplate.query("""
				SELECT id, title, slug, content, status, published_at, unpublish_at, created_at, updated_at
				FROM news
				WHERE slug = ?
				""", (rs, rowNum) -> mapNews(rs), slug);
		return rows.stream().findFirst().orElse(null);
	}

	long createNews(AdminNewsRequest request) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO news (title, slug, content, status, published_at, unpublish_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				cleanRequired(request.title()),
				cleanRequired(request.slug()),
				cleanRequired(request.content()),
				cleanRequired(request.status()),
				cleanOptional(request.publishedAt()),
				cleanOptional(request.unpublishAt()),
				now,
				now);
		Long newsId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return newsId == null ? 0 : newsId;
	}

	boolean updateNews(long newsId, AdminNewsRequest request) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE news
				SET title = ?, slug = ?, content = ?, status = ?, published_at = ?, unpublish_at = ?, updated_at = ?
				WHERE id = ?
				""",
				cleanRequired(request.title()),
				cleanRequired(request.slug()),
				cleanRequired(request.content()),
				cleanRequired(request.status()),
				cleanOptional(request.publishedAt()),
				cleanOptional(request.unpublishAt()),
				now,
				newsId) > 0;
	}

	private static AdminNewsResponse mapNews(ResultSet rs) throws SQLException {
		String status = rs.getString("status");
		return new AdminNewsResponse(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("slug"),
				rs.getString("content"),
				status,
				statusLabel(status),
				rs.getString("published_at"),
				rs.getString("unpublish_at"),
				rs.getString("created_at"),
				rs.getString("updated_at"));
	}

	static String cleanOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static String cleanRequired(String value) {
		return value == null ? "" : value.trim();
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "DRAFT" -> "草稿";
			case "PUBLISHED" -> "已發布";
			case "ARCHIVED" -> "已封存";
			default -> status;
		};
	}
}
