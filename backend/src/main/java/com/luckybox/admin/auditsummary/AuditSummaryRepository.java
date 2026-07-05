package com.luckybox.admin.auditsummary;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AuditSummaryRepository {

	private final JdbcTemplate jdbcTemplate;

	AuditSummaryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	CampaignBasics findCampaign(String slug) {
		List<CampaignBasics> rows = jdbcTemplate.query("""
				SELECT id, slug, title, status, fairness_mode, total_tickets, remaining_tickets,
					has_last_prize, seed_hash, revealed_seed
				FROM kuji_campaigns
				WHERE slug = ?
				""", (rs, rowNum) -> new CampaignBasics(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				rs.getString("status"),
				rs.getString("fairness_mode"),
				rs.getInt("total_tickets"),
				rs.getInt("remaining_tickets"),
				rs.getInt("has_last_prize") == 1,
				rs.getString("seed_hash"),
				rs.getString("revealed_seed")), slug);
		return rows.stream().findFirst().orElse(null);
	}

	List<PrizeDistributionResponse> findPrizeDistribution(long campaignId) {
		return jdbcTemplate.query("""
				SELECT p.rank, p.name, p.original_quantity, p.remaining_quantity, p.is_last_prize,
					(SELECT COUNT(*) FROM draw_results r WHERE r.prize_id = p.id) AS drawn_count
				FROM prizes p
				WHERE p.campaign_id = ?
				ORDER BY p.sort_order
				""", (rs, rowNum) -> new PrizeDistributionResponse(
				rs.getString("rank"),
				rs.getString("name"),
				rs.getInt("original_quantity"),
				rs.getInt("drawn_count"),
				rs.getInt("remaining_quantity"),
				rs.getInt("is_last_prize") == 1), campaignId);
	}

	DrawStats findDrawStats(long campaignId) {
		return jdbcTemplate.queryForObject("""
				SELECT
					COUNT(*) AS total_draws,
					COUNT(DISTINCT user_id) AS unique_drawers,
					COUNT(DISTINCT draw_order_id) AS total_orders,
					MIN(created_at) AS first_draw_at,
					MAX(created_at) AS last_draw_at
				FROM draw_results
				WHERE campaign_id = ?
				""", (rs, rowNum) -> new DrawStats(
				rs.getInt("total_draws"),
				rs.getInt("unique_drawers"),
				rs.getInt("total_orders"),
				rs.getString("first_draw_at"),
				rs.getString("last_draw_at")), campaignId);
	}

	record CampaignBasics(
			long id,
			String slug,
			String title,
			String status,
			String fairnessMode,
			int totalTickets,
			int remainingTickets,
			boolean hasLastPrize,
			String seedHash,
			String revealedSeed) {
	}

	record DrawStats(
			int totalDraws,
			int uniqueDrawers,
			int totalOrders,
			String firstDrawAt,
			String lastDrawAt) {
	}
}
