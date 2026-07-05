package com.luckybox.admin.campaign;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminPrizeLibraryRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminPrizeLibraryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminPrizeLibraryResponse> findPrizes(
			String campaignStatus,
			String rank,
			Boolean lastPrize,
			String keyword,
			int limit) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (campaignStatus != null) {
			where.append("AND c.status = ?\n");
			params.add(campaignStatus);
		}
		if (rank != null) {
			where.append("AND lower(p.rank) = ?\n");
			params.add(rank.toLowerCase());
		}
		if (lastPrize != null) {
			where.append("AND p.is_last_prize = ?\n");
			params.add(lastPrize ? 1 : 0);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(p.name) LIKE ?
						OR lower(p.rank) LIKE ?
						OR lower(p.description) LIKE ?
						OR lower(c.slug) LIKE ?
						OR lower(c.title) LIKE ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(like);
		}
		params.add(limit);
		return jdbcTemplate.query("""
				SELECT
					p.id, p.campaign_id, c.slug AS campaign_slug, c.title AS campaign_title,
					c.status AS campaign_status, p.rank, p.name, p.description, p.image_url,
					p.original_quantity, p.remaining_quantity, p.sort_order, p.is_last_prize,
					p.created_at, p.updated_at,
					COUNT(t.id) AS generated_tickets,
					COALESCE(SUM(CASE WHEN t.status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS available_tickets,
					COALESCE(SUM(CASE WHEN t.status = 'DRAWN' THEN 1 ELSE 0 END), 0) AS drawn_tickets
				FROM prizes p
				JOIN kuji_campaigns c ON c.id = p.campaign_id
				LEFT JOIN kuji_tickets t ON t.prize_id = p.id
				""" + where + """
				GROUP BY p.id
				ORDER BY c.updated_at DESC, c.id DESC, p.sort_order ASC, p.id ASC
				LIMIT ?
				""", (rs, rowNum) -> mapPrize(rs), params.toArray());
	}

	private static AdminPrizeLibraryResponse mapPrize(ResultSet rs) throws SQLException {
		String campaignStatus = rs.getString("campaign_status");
		return new AdminPrizeLibraryResponse(
				rs.getLong("id"),
				rs.getLong("campaign_id"),
				rs.getString("campaign_slug"),
				rs.getString("campaign_title"),
				campaignStatus,
				statusLabel(campaignStatus),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getString("image_url"),
				rs.getInt("original_quantity"),
				rs.getInt("remaining_quantity"),
				rs.getInt("generated_tickets"),
				rs.getInt("available_tickets"),
				rs.getInt("drawn_tickets"),
				rs.getInt("sort_order"),
				rs.getInt("is_last_prize") == 1,
				rs.getString("created_at"),
				rs.getString("updated_at"));
	}

	static String statusLabel(String status) {
		return switch (status) {
			case "DRAFT" -> "草稿";
			case "LIVE" -> "開抽中";
			case "SCHEDULED" -> "即將開抽";
			case "SOLD_OUT" -> "已完抽";
			case "PAUSED" -> "暫停中";
			case "ENDED" -> "已結束";
			default -> status;
		};
	}
}
