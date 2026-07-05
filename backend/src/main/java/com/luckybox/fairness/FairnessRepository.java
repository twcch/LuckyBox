package com.luckybox.fairness;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class FairnessRepository {

	private final JdbcTemplate jdbcTemplate;

	FairnessRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	FairnessCampaign findCampaign(String slug) {
		List<FairnessCampaign> rows = jdbcTemplate.query("""
				SELECT id, slug, fairness_mode, status, seed_hash, revealed_seed, total_tickets, remaining_tickets
				FROM kuji_campaigns
				WHERE slug = ?
				""", (rs, rowNum) -> new FairnessCampaign(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("fairness_mode"),
				rs.getString("status"),
				rs.getString("seed_hash"),
				rs.getString("revealed_seed"),
				rs.getInt("total_tickets"),
				rs.getInt("remaining_tickets")), slug);
		return rows.stream().findFirst().orElse(null);
	}

	List<FairnessDrawResponse> findDraws(long campaignId) {
		return jdbcTemplate.query("""
				SELECT t.serial_number, p.rank, p.name, r.random_proof
				FROM draw_results r
				JOIN kuji_tickets t ON t.id = r.ticket_id
				JOIN prizes p ON p.id = r.prize_id
				WHERE r.campaign_id = ?
				ORDER BY r.id
				""", (rs, rowNum) -> new FairnessDrawResponse(
				rowNum + 1,
				rs.getString("serial_number"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getString("random_proof")), campaignId);
	}

	record FairnessCampaign(
			long id,
			String slug,
			String fairnessMode,
			String status,
			String seedHash,
			String revealedSeed,
			int totalTickets,
			int remainingTickets) {
	}
}
