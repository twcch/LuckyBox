package com.luckybox.leaderboard;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class LeaderboardRepository {

	private final JdbcTemplate jdbcTemplate;

	LeaderboardRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<LeaderboardLiveDrawResponse> latestDraws(int limit) {
		return jdbcTemplate.query("""
				SELECT
					r.id,
					r.draw_order_id,
					u.display_name,
					c.slug AS campaign_slug,
					c.title AS campaign_title,
					p.rank AS prize_rank,
					p.name AS prize_name,
					r.result_index,
					r.created_at
				FROM draw_results r
				JOIN users u ON u.id = r.user_id
				JOIN kuji_campaigns c ON c.id = r.campaign_id
				JOIN prizes p ON p.id = r.prize_id
				WHERE c.status IN ('LIVE', 'SCHEDULED', 'SOLD_OUT')
				ORDER BY r.id DESC
				LIMIT ?
				""", (rs, rowNum) -> new LeaderboardLiveDrawResponse(
				rs.getLong("id"),
				rs.getLong("draw_order_id"),
				maskDisplayName(rs.getString("display_name")),
				rs.getString("campaign_slug"),
				rs.getString("campaign_title"),
				rs.getString("prize_rank"),
				rs.getString("prize_name"),
				rs.getInt("result_index"),
				rs.getString("created_at")), limit);
	}

	List<LeaderboardLiveDrawResponse> latestDrawsForCampaign(String slug, int limit) {
		return jdbcTemplate.query("""
				SELECT
					r.id,
					r.draw_order_id,
					u.display_name,
					c.slug AS campaign_slug,
					c.title AS campaign_title,
					p.rank AS prize_rank,
					p.name AS prize_name,
					r.result_index,
					r.created_at
				FROM draw_results r
				JOIN users u ON u.id = r.user_id
				JOIN kuji_campaigns c ON c.id = r.campaign_id
				JOIN prizes p ON p.id = r.prize_id
				WHERE c.slug = ?
				  AND c.status IN ('LIVE', 'SCHEDULED', 'SOLD_OUT')
				ORDER BY r.id DESC
				LIMIT ?
				""", (rs, rowNum) -> new LeaderboardLiveDrawResponse(
				rs.getLong("id"),
				rs.getLong("draw_order_id"),
				maskDisplayName(rs.getString("display_name")),
				rs.getString("campaign_slug"),
				rs.getString("campaign_title"),
				rs.getString("prize_rank"),
				rs.getString("prize_name"),
				rs.getInt("result_index"),
				rs.getString("created_at")), slug, limit);
	}

	List<LeaderboardLuckyMemberResponse> luckyMembers(int limit) {
		return jdbcTemplate.query("""
				SELECT
					u.display_name,
					COUNT(*) AS lucky_wins,
					SUM(CASE WHEN p.rank = 'S' THEN 1 ELSE 0 END) AS top_rank_wins,
					SUM(CASE WHEN p.is_last_prize = 1 THEN 1 ELSE 0 END) AS last_prize_wins
				FROM draw_results r
				JOIN users u ON u.id = r.user_id
				JOIN prizes p ON p.id = r.prize_id
				JOIN kuji_campaigns c ON c.id = r.campaign_id
				WHERE (p.rank IN ('S', 'A') OR p.is_last_prize = 1)
				  AND c.status IN ('LIVE', 'SCHEDULED', 'SOLD_OUT')
				GROUP BY r.user_id, u.display_name
				ORDER BY lucky_wins DESC, top_rank_wins DESC, last_prize_wins DESC, u.display_name ASC
				LIMIT ?
				""", (rs, rowNum) -> new LeaderboardLuckyMemberResponse(
				rowNum + 1,
				maskDisplayName(rs.getString("display_name")),
				rs.getInt("lucky_wins"),
				rs.getInt("top_rank_wins"),
				rs.getInt("last_prize_wins")), limit);
	}

	List<LeaderboardPopularCampaignResponse> popularCampaigns(int limit) {
		return jdbcTemplate.query("""
				SELECT
					c.id,
					c.slug,
					c.title,
					c.status,
					c.price_per_draw,
					c.total_tickets,
					c.remaining_tickets,
					COALESCE(SUM(d.quantity), 0) AS draw_count,
					COUNT(DISTINCT d.user_id) AS unique_drawers,
					(
						SELECT p.rank || '賞剩 ' || p.remaining_quantity
						FROM prizes p
						WHERE p.campaign_id = c.id
						  AND p.is_last_prize = 0
						  AND p.remaining_quantity > 0
						ORDER BY p.sort_order
						LIMIT 1
					) AS rare_hint
				FROM kuji_campaigns c
				LEFT JOIN draw_orders d ON d.campaign_id = c.id AND d.status = 'COMPLETED'
				WHERE c.status IN ('LIVE', 'SCHEDULED', 'SOLD_OUT')
				GROUP BY
					c.id, c.slug, c.title, c.status, c.price_per_draw, c.total_tickets, c.remaining_tickets
				ORDER BY
					draw_count DESC,
					(c.total_tickets - c.remaining_tickets) DESC,
					CASE c.status WHEN 'LIVE' THEN 0 WHEN 'SCHEDULED' THEN 1 ELSE 2 END,
					c.id ASC
				LIMIT ?
				""", (rs, rowNum) -> mapPopularCampaign(rs), limit);
	}

	private static LeaderboardPopularCampaignResponse mapPopularCampaign(ResultSet rs) throws SQLException {
		int totalTickets = rs.getInt("total_tickets");
		int remainingTickets = rs.getInt("remaining_tickets");
		int soldTickets = Math.max(totalTickets - remainingTickets, 0);
		String status = rs.getString("status");
		return new LeaderboardPopularCampaignResponse(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				status,
				statusLabel(status),
				rs.getInt("price_per_draw"),
				totalTickets,
				remainingTickets,
				soldTickets,
				soldRate(soldTickets, totalTickets),
				rs.getInt("draw_count"),
				rs.getInt("unique_drawers"),
				rareHint(rs.getString("rare_hint")));
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

	private static double soldRate(int soldTickets, int totalTickets) {
		if (totalTickets == 0) {
			return 0;
		}
		return Math.round((soldTickets * 1000.0 / totalTickets)) / 10.0;
	}

	private static String rareHint(String rareHint) {
		if (rareHint != null && !rareHint.isBlank()) {
			return rareHint;
		}
		return "公開剩餘數";
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "LIVE" -> "開抽中";
			case "SCHEDULED" -> "即將開抽";
			case "SOLD_OUT" -> "已完抽";
			default -> status;
		};
	}
}
