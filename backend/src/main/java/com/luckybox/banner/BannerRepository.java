package com.luckybox.banner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class BannerRepository {

	private final JdbcTemplate jdbcTemplate;

	BannerRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<BannerSummary> findActiveBanners(String position) {
		List<Object> params = new ArrayList<>();
		// 排程上下架：ACTIVE 且現在落在 [publish_at, unpublish_at) 時間窗內才公開（NULL 視為無限制）。
		String now = Instant.now().toString();
		StringBuilder where = new StringBuilder(
				"WHERE status = 'ACTIVE'\n"
						+ "AND (publish_at IS NULL OR publish_at <= ?)\n"
						+ "AND (unpublish_at IS NULL OR unpublish_at > ?)\n");
		params.add(now);
		params.add(now);
		if (position != null) {
			where.append("AND position = ?\n");
			params.add(position);
		}
		return jdbcTemplate.query("""
				SELECT id, title, image_url, href, position
				FROM banners
				""" + where + """
				ORDER BY id DESC
				LIMIT 10
				""", (rs, rowNum) -> new BannerSummary(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("image_url"),
				rs.getString("href"),
				rs.getString("position")), params.toArray());
	}
}
