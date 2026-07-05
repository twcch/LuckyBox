package com.luckybox.admin.consistency;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ConsistencyRepository {

	private final JdbcTemplate jdbcTemplate;

	ConsistencyRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	int countCampaigns() {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kuji_campaigns", Integer.class);
		return count == null ? 0 : count;
	}

	/** A campaign's remaining_tickets counter must equal its live AVAILABLE ticket count. */
	List<ConsistencyFinding> findRemainingTicketMismatches() {
		return jdbcTemplate.query("""
				SELECT c.id, c.slug, c.remaining_tickets AS remaining,
					(SELECT COUNT(*) FROM kuji_tickets t WHERE t.campaign_id = c.id AND t.status = 'AVAILABLE') AS available
				FROM kuji_campaigns c
				WHERE (SELECT COUNT(*) FROM kuji_tickets t2 WHERE t2.campaign_id = c.id) > 0
				  AND c.remaining_tickets <> (
						SELECT COUNT(*) FROM kuji_tickets t3 WHERE t3.campaign_id = c.id AND t3.status = 'AVAILABLE')
				""", (rs, rowNum) -> new ConsistencyFinding(
				rs.getLong("id"),
				rs.getString("slug"),
				"REMAINING_TICKET_MISMATCH",
				"remaining_tickets=" + rs.getInt("remaining") + " 但可抽票數=" + rs.getInt("available")));
	}

	/** Every DRAWN ticket must correspond to exactly one draw_result, and vice versa. */
	List<ConsistencyFinding> findDrawnResultMismatches() {
		return jdbcTemplate.query("""
				SELECT c.id, c.slug,
					(SELECT COUNT(*) FROM kuji_tickets t WHERE t.campaign_id = c.id AND t.status = 'DRAWN') AS drawn,
					(SELECT COUNT(*) FROM draw_results r WHERE r.campaign_id = c.id) AS results
				FROM kuji_campaigns c
				WHERE (SELECT COUNT(*) FROM kuji_tickets t2 WHERE t2.campaign_id = c.id AND t2.status = 'DRAWN')
					<> (SELECT COUNT(*) FROM draw_results r2 WHERE r2.campaign_id = c.id)
				""", (rs, rowNum) -> new ConsistencyFinding(
				rs.getLong("id"),
				rs.getString("slug"),
				"DRAWN_RESULT_MISMATCH",
				"DRAWN 票數=" + rs.getInt("drawn") + " 但 draw_results=" + rs.getInt("results")));
	}

	/** A sold-out HASH_COMMIT_REVEAL campaign that committed a seed must have revealed it. */
	List<ConsistencyFinding> findUnrevealedSoldOutCampaigns() {
		return jdbcTemplate.query("""
				SELECT id, slug FROM kuji_campaigns
				WHERE status = 'SOLD_OUT' AND fairness_mode = 'HASH_COMMIT_REVEAL'
				  AND server_seed IS NOT NULL AND revealed_seed IS NULL
				""", (rs, rowNum) -> new ConsistencyFinding(
				rs.getLong("id"),
				rs.getString("slug"),
				"SEED_NOT_REVEALED",
				"已完抽的 HASH_COMMIT_REVEAL 賞池尚未公開 revealed_seed"));
	}

	/** Each non-last prize's remaining_quantity must equal its live AVAILABLE ticket count. */
	List<ConsistencyFinding> findPrizeQuantityMismatches() {
		return jdbcTemplate.query("""
				SELECT p.campaign_id AS campaign_id, c.slug, p.rank, p.remaining_quantity AS remaining,
					(SELECT COUNT(*) FROM kuji_tickets t WHERE t.prize_id = p.id AND t.status = 'AVAILABLE') AS available
				FROM prizes p
				JOIN kuji_campaigns c ON c.id = p.campaign_id
				WHERE p.is_last_prize = 0
				  AND (SELECT COUNT(*) FROM kuji_tickets t2 WHERE t2.prize_id = p.id) > 0
				  AND p.remaining_quantity <> (
						SELECT COUNT(*) FROM kuji_tickets t3 WHERE t3.prize_id = p.id AND t3.status = 'AVAILABLE')
				""", (rs, rowNum) -> new ConsistencyFinding(
				rs.getLong("campaign_id"),
				rs.getString("slug"),
				"PRIZE_QUANTITY_MISMATCH",
				"獎項 " + rs.getString("rank") + " remaining_quantity=" + rs.getInt("remaining")
						+ " 但可抽票=" + rs.getInt("available")));
	}
}
