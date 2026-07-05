package com.luckybox.admin.banner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminBannerRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminBannerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminBannerResponse> findBanners(String status, String position, String keyword) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND b.status = ?\n");
			params.add(status);
		}
		if (position != null) {
			where.append("AND b.position = ?\n");
			params.add(position);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(b.title) LIKE ?
						OR lower(b.image_url) LIKE ?
						OR lower(COALESCE(b.href, '')) LIKE ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
		}
		return jdbcTemplate.query("""
				SELECT id, title, image_url, href, position, status, publish_at, unpublish_at, created_at, updated_at
				FROM banners b
				""" + where + """
				ORDER BY id DESC
				LIMIT 100
				""", (rs, rowNum) -> mapBanner(rs), params.toArray());
	}

	AdminBannerResponse findBanner(long bannerId) {
		List<AdminBannerResponse> rows = jdbcTemplate.query("""
				SELECT id, title, image_url, href, position, status, publish_at, unpublish_at, created_at, updated_at
				FROM banners
				WHERE id = ?
				""", (rs, rowNum) -> mapBanner(rs), bannerId);
		return rows.stream().findFirst().orElse(null);
	}

	long createBanner(AdminBannerRequest request) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO banners (
					title, image_url, href, position, status, publish_at, unpublish_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				cleanRequired(request.title()),
				cleanRequired(request.imageUrl()),
				cleanOptional(request.href()),
				cleanRequired(request.position()),
				cleanRequired(request.status()),
				cleanOptional(request.publishAt()),
				cleanOptional(request.unpublishAt()),
				now,
				now);
		Long bannerId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return bannerId == null ? 0 : bannerId;
	}

	boolean updateBanner(long bannerId, AdminBannerRequest request) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE banners
				SET title = ?, image_url = ?, href = ?, position = ?, status = ?,
					publish_at = ?, unpublish_at = ?, updated_at = ?
				WHERE id = ?
				""",
				cleanRequired(request.title()),
				cleanRequired(request.imageUrl()),
				cleanOptional(request.href()),
				cleanRequired(request.position()),
				cleanRequired(request.status()),
				cleanOptional(request.publishAt()),
				cleanOptional(request.unpublishAt()),
				now,
				bannerId) > 0;
	}

	private static AdminBannerResponse mapBanner(ResultSet rs) throws SQLException {
		String position = rs.getString("position");
		String status = rs.getString("status");
		return new AdminBannerResponse(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("image_url"),
				rs.getString("href"),
				position,
				positionLabel(position),
				status,
				statusLabel(status),
				rs.getString("publish_at"),
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

	private static String positionLabel(String position) {
		return switch (position) {
			case "HOME_HERO" -> "首頁主視覺";
			case "HOME_SECTION" -> "首頁區塊";
			default -> position;
		};
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "DRAFT" -> "草稿";
			case "ACTIVE" -> "啟用";
			case "ARCHIVED" -> "已封存";
			default -> status;
		};
	}
}
