package com.luckybox.news;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class NewsRepository {

	private final JdbcTemplate jdbcTemplate;

	NewsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// 排程上下架：PUBLISHED 且現在落在 [published_at, unpublish_at) 時間窗內才公開（NULL 視為無限制）。
	private static final String SCHEDULE_WINDOW =
			"AND (published_at IS NULL OR published_at <= ?)\n"
					+ "AND (unpublish_at IS NULL OR unpublish_at > ?)\n";

	List<NewsSummary> findPublishedNews() {
		String now = Instant.now().toString();
		return jdbcTemplate.query("""
				SELECT id, title, slug, content, published_at
				FROM news
				WHERE status = 'PUBLISHED'
				""" + SCHEDULE_WINDOW + """
				ORDER BY COALESCE(published_at, created_at) DESC, id DESC
				LIMIT 50
				""", (rs, rowNum) -> new NewsSummary(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("slug"),
				excerpt(rs.getString("content")),
				rs.getString("published_at")), now, now);
	}

	Optional<NewsDetail> findPublishedBySlug(String slug) {
		String now = Instant.now().toString();
		List<NewsDetail> rows = jdbcTemplate.query("""
				SELECT id, title, slug, content, published_at
				FROM news
				WHERE slug = ? AND status = 'PUBLISHED'
				""" + SCHEDULE_WINDOW, (rs, rowNum) -> new NewsDetail(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("slug"),
				rs.getString("content"),
				rs.getString("published_at")), slug, now, now);
		return rows.stream().findFirst();
	}

	private static String excerpt(String content) {
		if (content == null || content.isBlank()) {
			return "";
		}
		String normalized = content.trim().replaceAll("\\s+", " ");
		return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
	}
}
