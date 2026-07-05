package com.luckybox.admin.campaign;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.luckybox.common.PiiMasking;

@Repository
class AdminCampaignPrizeRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminCampaignPrizeRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	CampaignRow findCampaign(long campaignId) {
		List<CampaignRow> campaigns = jdbcTemplate.query("""
				SELECT id, slug, status
				FROM kuji_campaigns
				WHERE id = ?
				""", (rs, rowNum) -> new CampaignRow(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("status")), campaignId);
		return campaigns.stream().findFirst().orElse(null);
	}

	AdminCampaignPrizeOverviewResponse overview(long campaignId) {
		List<AdminCampaignPrizeResponse> prizes = prizes(campaignId);
		return new AdminCampaignPrizeOverviewResponse(
				campaignId,
				AdminCampaignPrizeMath.totalRegularQuantity(prizes),
				AdminCampaignPrizeMath.remainingRegularQuantity(prizes),
				intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ?", campaignId),
				intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ? AND status = 'AVAILABLE'", campaignId),
				prizes);
	}

	List<AdminCampaignPrizeResponse> prizes(long campaignId) {
		return jdbcTemplate.query("""
				SELECT
					p.id, p.campaign_id, p.rank, p.name, p.description, p.image_url,
					p.original_quantity, p.remaining_quantity, p.sort_order, p.is_last_prize,
					p.created_at, p.updated_at,
					COUNT(t.id) AS generated_tickets,
					COALESCE(SUM(CASE WHEN t.status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS available_tickets
				FROM prizes p
				LEFT JOIN kuji_tickets t ON t.prize_id = p.id
				WHERE p.campaign_id = ?
				GROUP BY p.id
				ORDER BY p.sort_order, p.id
				""", (rs, rowNum) -> mapPrize(rs), campaignId);
	}

	List<AdminCampaignTicketResponse> tickets(long campaignId, String status, String keyword, int limit) {
		List<Object> params = new java.util.ArrayList<>();
		params.add(campaignId);
		StringBuilder where = new StringBuilder("WHERE t.campaign_id = ?\n");
		if (status != null) {
			where.append("AND t.status = ?\n");
			params.add(status);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(t.serial_number) LIKE ?
						OR lower(t.status) LIKE ?
						OR lower(p.rank) LIKE ?
						OR lower(p.name) LIKE ?
						OR CAST(t.id AS TEXT) = ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(keyword);
		}
		params.add(limit);
		return jdbcTemplate.query("""
				SELECT
					t.id, t.campaign_id, t.prize_id, t.serial_number, t.status, t.draw_id,
					t.drawn_by_user_id, t.drawn_at, t.created_at, t.updated_at,
					p.rank AS prize_rank, p.name AS prize_name, p.is_last_prize,
					u.display_name AS drawn_by_display_name, u.email AS drawn_by_email
				FROM kuji_tickets t
				JOIN prizes p ON p.id = t.prize_id
				LEFT JOIN users u ON u.id = t.drawn_by_user_id
				""" + where + """
				ORDER BY t.id
				LIMIT ?
				""", (rs, rowNum) -> mapTicket(rs), params.toArray());
	}

	AdminCampaignPrizeResponse findPrize(long campaignId, long prizeId) {
		List<AdminCampaignPrizeResponse> prizes = jdbcTemplate.query("""
				SELECT
					p.id, p.campaign_id, p.rank, p.name, p.description, p.image_url,
					p.original_quantity, p.remaining_quantity, p.sort_order, p.is_last_prize,
					p.created_at, p.updated_at,
					COUNT(t.id) AS generated_tickets,
					COALESCE(SUM(CASE WHEN t.status = 'AVAILABLE' THEN 1 ELSE 0 END), 0) AS available_tickets
				FROM prizes p
				LEFT JOIN kuji_tickets t ON t.prize_id = p.id
				WHERE p.campaign_id = ? AND p.id = ?
				GROUP BY p.id
				""", (rs, rowNum) -> mapPrize(rs), campaignId, prizeId);
		return prizes.stream().findFirst().orElse(null);
	}

	long createPrize(long campaignId, AdminCampaignPrizeRequest request) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				campaignId,
				cleanRequired(request.rank()),
				cleanRequired(request.name()),
				cleanOptional(request.description()),
				cleanOptional(request.imageUrl()),
				request.originalQuantity(),
				request.originalQuantity(),
				request.sortOrder(),
				request.lastPrize() ? 1 : 0,
				now,
				now);
		Long prizeId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return prizeId == null ? 0 : prizeId;
	}

	boolean updatePrize(long campaignId, long prizeId, AdminCampaignPrizeRequest request, int remainingQuantity) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE prizes
				SET rank = ?, name = ?, description = ?, image_url = ?, original_quantity = ?,
					remaining_quantity = ?, sort_order = ?, is_last_prize = ?, updated_at = ?
				WHERE id = ? AND campaign_id = ?
				""",
				cleanRequired(request.rank()),
				cleanRequired(request.name()),
				cleanOptional(request.description()),
				cleanOptional(request.imageUrl()),
				request.originalQuantity(),
				remainingQuantity,
				request.sortOrder(),
				request.lastPrize() ? 1 : 0,
				now,
				prizeId,
				campaignId) > 0;
	}

	int generatedTickets(long campaignId, long prizeId) {
		return intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ? AND prize_id = ?", campaignId, prizeId);
	}

	int drawnTickets(long campaignId, long prizeId) {
		return intValue("""
				SELECT COUNT(*)
				FROM kuji_tickets
				WHERE campaign_id = ? AND prize_id = ? AND status = 'DRAWN'
				""", campaignId, prizeId);
	}

	int availableTickets(long campaignId, long prizeId) {
		return intValue("""
				SELECT COUNT(*)
				FROM kuji_tickets
				WHERE campaign_id = ? AND prize_id = ? AND status = 'AVAILABLE'
				""", campaignId, prizeId);
	}

	int totalTickets(long campaignId) {
		return intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ?", campaignId);
	}

	int availableTickets(long campaignId) {
		return intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ? AND status = 'AVAILABLE'", campaignId);
	}

	int nonLastPrizeCount(long campaignId) {
		return intValue("""
				SELECT COUNT(*)
				FROM prizes
				WHERE campaign_id = ? AND is_last_prize = 0 AND original_quantity > 0
				""", campaignId);
	}

	int generateMissingTickets(CampaignRow campaign) {
		int generated = 0;
		List<PrizeTicketRow> prizes = jdbcTemplate.query("""
				SELECT id, rank, original_quantity
				FROM prizes
				WHERE campaign_id = ? AND is_last_prize = 0
				ORDER BY sort_order, id
				""", (rs, rowNum) -> new PrizeTicketRow(
				rs.getLong("id"),
				rs.getString("rank"),
				rs.getInt("original_quantity")), campaign.id());
		int nextSerial = totalTickets(campaign.id()) + 1;
		for (PrizeTicketRow prize : prizes) {
			int existingTickets = generatedTickets(campaign.id(), prize.id());
			int missingTickets = prize.originalQuantity() - existingTickets;
			for (int index = 0; index < missingTickets; index++) {
				insertTicket(campaign.id(), prize.id(), campaign.slug(), nextSerial++);
				generated++;
			}
		}
		syncPrizeRemainingQuantities(campaign.id());
		syncCampaignTicketCounts(campaign.id());
		return generated;
	}

	void syncCampaignFromPrizesWhenNoTickets(long campaignId) {
		if (totalTickets(campaignId) > 0) {
			return;
		}
		int totalQuantity = intValue("""
				SELECT COALESCE(SUM(original_quantity), 0)
				FROM prizes
				WHERE campaign_id = ? AND is_last_prize = 0
				""", campaignId);
		int remainingQuantity = intValue("""
				SELECT COALESCE(SUM(remaining_quantity), 0)
				FROM prizes
				WHERE campaign_id = ? AND is_last_prize = 0
				""", campaignId);
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET total_tickets = ?, remaining_tickets = ?, updated_at = ?
				WHERE id = ?
				""", totalQuantity, remainingQuantity, now, campaignId);
	}

	void syncCampaignTicketCounts(long campaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET total_tickets = (
						SELECT COUNT(*)
						FROM kuji_tickets
						WHERE campaign_id = ?
					),
					remaining_tickets = (
						SELECT COUNT(*)
						FROM kuji_tickets
						WHERE campaign_id = ? AND status = 'AVAILABLE'
					),
					updated_at = ?
				WHERE id = ?
				""", campaignId, campaignId, now, campaignId);
	}

	void syncPrizeRemainingQuantities(long campaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE prizes
				SET remaining_quantity = (
						SELECT COUNT(*)
						FROM kuji_tickets
						WHERE prize_id = prizes.id AND status = 'AVAILABLE'
					),
					updated_at = ?
				WHERE campaign_id = ? AND is_last_prize = 0
				""", now, campaignId);
	}

	private void insertTicket(long campaignId, long prizeId, String slug, int serial) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO kuji_tickets (
					campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
				)
				VALUES (?, ?, ?, 'AVAILABLE', NULL, NULL, NULL, ?, ?)
				""", campaignId, prizeId, serialNumber(slug, serial), now, now);
	}

	private static AdminCampaignPrizeResponse mapPrize(ResultSet rs) throws SQLException {
		return new AdminCampaignPrizeResponse(
				rs.getLong("id"),
				rs.getLong("campaign_id"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getString("image_url"),
				rs.getInt("original_quantity"),
				rs.getInt("remaining_quantity"),
				rs.getInt("generated_tickets"),
				rs.getInt("available_tickets"),
				rs.getInt("sort_order"),
				rs.getInt("is_last_prize") == 1,
				rs.getString("created_at"),
				rs.getString("updated_at"));
	}

	private static AdminCampaignTicketResponse mapTicket(ResultSet rs) throws SQLException {
		Long drawnByUserId = longOrNull(rs, "drawn_by_user_id");
		String drawnByEmail = drawnByUserId == null ? null : PiiMasking.maskEmail(rs.getString("drawn_by_email"));
		return new AdminCampaignTicketResponse(
				rs.getLong("id"),
				rs.getLong("campaign_id"),
				rs.getLong("prize_id"),
				rs.getString("serial_number"),
				rs.getString("status"),
				ticketStatusLabel(rs.getString("status")),
				rs.getString("prize_rank"),
				rs.getString("prize_name"),
				rs.getInt("is_last_prize") == 1,
				longOrNull(rs, "draw_id"),
				drawnByUserId,
				drawnByUserId == null ? null : rs.getString("drawn_by_display_name"),
				drawnByEmail,
				rs.getString("drawn_at"),
				rs.getString("created_at"),
				rs.getString("updated_at"));
	}

	private int intValue(String sql, Object... params) {
		Number value = jdbcTemplate.queryForObject(sql, Number.class, params);
		return value == null ? 0 : value.intValue();
	}

	private static Long longOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	static String ticketStatusLabel(String status) {
		return switch (status) {
			case "AVAILABLE" -> "可抽";
			case "DRAWN" -> "已抽出";
			case "VOIDED" -> "已作廢";
			default -> status;
		};
	}

	private static String serialNumber(String slug, int serial) {
		return slug.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "")
				+ "-" + String.format("%04d", serial);
	}

	static String cleanOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static String cleanRequired(String value) {
		return value == null ? "" : value.trim();
	}

	record CampaignRow(long id, String slug, String status) {
	}

	private record PrizeTicketRow(long id, String rank, int originalQuantity) {
	}
}
