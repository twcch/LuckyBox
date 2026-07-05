package com.luckybox.admin.campaign;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminCampaignRepository {

	private final JdbcTemplate jdbcTemplate;

	AdminCampaignRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<AdminCampaignResponse> findCampaigns(String status, String keyword, String sort) {
		List<Object> params = new ArrayList<>();
		StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
		if (status != null) {
			where.append("AND c.status = ?\n");
			params.add(status);
		}
		if (keyword != null) {
			where.append("""
					AND (
						lower(c.slug) LIKE ?
						OR lower(c.title) LIKE ?
						OR lower(c.subtitle) LIKE ?
						OR lower(c.brand_name) LIKE ?
					)
					""");
			String like = "%" + keyword.toLowerCase() + "%";
			params.add(like);
			params.add(like);
			params.add(like);
			params.add(like);
		}
		return jdbcTemplate.query("""
				SELECT c.*, COUNT(p.id) AS prize_count
				FROM kuji_campaigns c
				LEFT JOIN prizes p ON p.campaign_id = c.id
				""" + where + """
				GROUP BY c.id
				ORDER BY
				""" + sortClause(sort) + """
				""", (rs, rowNum) -> mapCampaign(rs), params.toArray());
	}

	AdminCampaignResponse findCampaign(long campaignId) {
		List<AdminCampaignResponse> campaigns = jdbcTemplate.query("""
				SELECT c.*, COUNT(p.id) AS prize_count
				FROM kuji_campaigns c
				LEFT JOIN prizes p ON p.campaign_id = c.id
				WHERE c.id = ?
				GROUP BY c.id
				""", (rs, rowNum) -> mapCampaign(rs), campaignId);
		return campaigns.stream().findFirst().orElse(null);
	}

	AdminCampaignResponse findCampaignBySlug(String slug) {
		List<AdminCampaignResponse> campaigns = jdbcTemplate.query("""
				SELECT c.*, COUNT(p.id) AS prize_count
				FROM kuji_campaigns c
				LEFT JOIN prizes p ON p.campaign_id = c.id
				WHERE c.slug = ?
				GROUP BY c.id
				""", (rs, rowNum) -> mapCampaign(rs), slug);
		return campaigns.stream().findFirst().orElse(null);
	}

	long createCampaign(AdminCampaignRequest request) {
		String now = Instant.now().toString();
		try {
			jdbcTemplate.update("""
					INSERT INTO kuji_campaigns (
						slug, title, subtitle, description, cover_image_url, banner_image_url, source_type,
						commercial_use_confirmed, official_license_confirmed, rights_notice,
						age_restricted, minimum_age, age_verification_note,
						ip_name, brand_name, price_per_draw, total_tickets, remaining_tickets, status,
						sales_start_at, sales_end_at, shipping_note, return_policy_note, has_last_prize,
						last_prize_rule, fairness_mode, seed_hash, revealed_seed, created_at, updated_at
					)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
					""",
					cleanRequired(request.slug()),
					cleanRequired(request.title()),
					cleanOptional(request.subtitle()),
					cleanRequired(request.description()),
					cleanOptional(request.coverImageUrl()),
					cleanOptional(request.bannerImageUrl()),
					cleanRequired(request.sourceType()),
					Boolean.TRUE.equals(request.commercialUseConfirmed()) ? 1 : 0,
					Boolean.TRUE.equals(request.officialLicenseConfirmed()) ? 1 : 0,
					cleanOptional(request.rightsNotice()),
					Boolean.TRUE.equals(request.ageRestricted()) ? 1 : 0,
					request.minimumAge(),
					cleanOptional(request.ageVerificationNote()),
					cleanOptional(request.ipName()),
					cleanOptional(request.brandName()),
					request.pricePerDraw(),
					request.totalTickets(),
					request.totalTickets(),
					cleanRequired(request.status()),
					cleanOptional(request.salesStartAt()),
					cleanOptional(request.salesEndAt()),
					cleanRequired(request.shippingNote()),
					cleanRequired(request.returnPolicyNote()),
					request.hasLastPrize() ? 1 : 0,
					cleanOptional(request.lastPrizeRule()),
					cleanRequired(request.fairnessMode()),
					cleanOptional(request.seedHash()),
					now,
					now);
		} catch (DataIntegrityViolationException exception) {
			throw exception;
		}
		Long campaignId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return campaignId == null ? 0 : campaignId;
	}

	boolean updateCampaign(long campaignId, AdminCampaignRequest request, int remainingTickets) {
		String now = Instant.now().toString();
		try {
			return jdbcTemplate.update("""
					UPDATE kuji_campaigns
					SET slug = ?, title = ?, subtitle = ?, description = ?, cover_image_url = ?, banner_image_url = ?,
						source_type = ?, commercial_use_confirmed = ?, official_license_confirmed = ?, rights_notice = ?,
						age_restricted = ?, minimum_age = ?, age_verification_note = ?,
						ip_name = ?, brand_name = ?, price_per_draw = ?, total_tickets = ?,
						remaining_tickets = ?, status = ?, sales_start_at = ?, sales_end_at = ?, shipping_note = ?,
						return_policy_note = ?, has_last_prize = ?, last_prize_rule = ?, fairness_mode = ?,
						seed_hash = ?, updated_at = ?
					WHERE id = ?
					""",
					cleanRequired(request.slug()),
					cleanRequired(request.title()),
					cleanOptional(request.subtitle()),
					cleanRequired(request.description()),
					cleanOptional(request.coverImageUrl()),
					cleanOptional(request.bannerImageUrl()),
					cleanRequired(request.sourceType()),
					Boolean.TRUE.equals(request.commercialUseConfirmed()) ? 1 : 0,
					Boolean.TRUE.equals(request.officialLicenseConfirmed()) ? 1 : 0,
					cleanOptional(request.rightsNotice()),
					Boolean.TRUE.equals(request.ageRestricted()) ? 1 : 0,
					request.minimumAge(),
					cleanOptional(request.ageVerificationNote()),
					cleanOptional(request.ipName()),
					cleanOptional(request.brandName()),
					request.pricePerDraw(),
					request.totalTickets(),
					remainingTickets,
					cleanRequired(request.status()),
					cleanOptional(request.salesStartAt()),
					cleanOptional(request.salesEndAt()),
					cleanRequired(request.shippingNote()),
					cleanRequired(request.returnPolicyNote()),
					request.hasLastPrize() ? 1 : 0,
					cleanOptional(request.lastPrizeRule()),
					cleanRequired(request.fairnessMode()),
					cleanOptional(request.seedHash()),
					now,
					campaignId) > 0;
		} catch (DataIntegrityViolationException exception) {
			throw exception;
		}
	}

	void commitSeedIfAbsent(long campaignId, String serverSeed, String seedHash) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET server_seed = ?, seed_hash = ?, updated_at = ?
				WHERE id = ? AND server_seed IS NULL
				""", serverSeed, seedHash, now, campaignId);
	}

	boolean updateStatus(long campaignId, String status) {
		String now = Instant.now().toString();
		return jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET status = ?, updated_at = ?
				WHERE id = ?
				""", cleanRequired(status), now, campaignId) > 0;
	}

	void copyPrizes(long sourceCampaignId, long targetCampaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO prizes (
					campaign_id, rank, name, description, image_url, original_quantity, remaining_quantity,
					sort_order, is_last_prize, created_at, updated_at
				)
				SELECT
					?, rank, name, description, image_url, original_quantity, original_quantity,
					sort_order, is_last_prize, ?, ?
				FROM prizes
				WHERE campaign_id = ?
				ORDER BY sort_order, id
				""", targetCampaignId, now, now, sourceCampaignId);
	}

	int totalTicketCount(long campaignId) {
		return intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ?", campaignId);
	}

	int availableTicketCount(long campaignId) {
		return intValue("SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ? AND status = 'AVAILABLE'", campaignId);
	}

	List<AdminCampaignDryRunResponse.Result> dryRunTickets(long campaignId, int quantity) {
		return jdbcTemplate.query("""
				SELECT t.serial_number, p.rank, p.name
				FROM kuji_tickets t
				JOIN prizes p ON p.id = t.prize_id
				WHERE t.campaign_id = ? AND t.status = 'AVAILABLE'
				ORDER BY t.id
				LIMIT ?
				""",
				(rs, rowNum) -> new AdminCampaignDryRunResponse.Result(
						rs.getString("serial_number"),
						rs.getString("rank"),
						rs.getString("name")),
				campaignId,
				quantity);
	}

	private static AdminCampaignResponse mapCampaign(ResultSet rs) throws SQLException {
		int totalTickets = rs.getInt("total_tickets");
		int remainingTickets = rs.getInt("remaining_tickets");
		String sourceType = rs.getString("source_type");
		String status = rs.getString("status");
		return new AdminCampaignResponse(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				rs.getString("subtitle"),
				rs.getString("description"),
				rs.getString("cover_image_url"),
				rs.getString("banner_image_url"),
				sourceType,
				sourceTypeLabel(sourceType),
				rs.getInt("commercial_use_confirmed") == 1,
				rs.getInt("official_license_confirmed") == 1,
				rs.getString("rights_notice"),
				rs.getInt("age_restricted") == 1,
				integerOrNull(rs, "minimum_age"),
				rs.getString("age_verification_note"),
				rs.getString("ip_name"),
				rs.getString("brand_name"),
				rs.getInt("price_per_draw"),
				totalTickets,
				remainingTickets,
				totalTickets - remainingTickets,
				rs.getInt("prize_count"),
				status,
				statusLabel(status),
				rs.getString("sales_start_at"),
				rs.getString("sales_end_at"),
				rs.getString("shipping_note"),
				rs.getString("return_policy_note"),
				rs.getInt("has_last_prize") == 1,
				rs.getString("last_prize_rule"),
				rs.getString("fairness_mode"),
				rs.getString("seed_hash"),
				rs.getString("created_at"),
				rs.getString("updated_at"));
	}

	static String cleanOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	static String cleanRequired(String value) {
		return value == null ? "" : value.trim();
	}

	private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private int intValue(String sql, Object... params) {
		Number value = jdbcTemplate.queryForObject(sql, Number.class, params);
		return value == null ? 0 : value.intValue();
	}

	private static String sortClause(String sort) {
		return switch (sort) {
			case "updatedDesc" -> "c.updated_at DESC, c.id DESC";
			case "titleAsc" -> "lower(c.title) ASC, c.id DESC";
			case "priceAsc" -> "c.price_per_draw ASC, c.id DESC";
			case "priceDesc" -> "c.price_per_draw DESC, c.id DESC";
			case "remainingAsc" -> "c.remaining_tickets ASC, c.id DESC";
			case "remainingDesc" -> "c.remaining_tickets DESC, c.id DESC";
			case "status" -> """
					CASE c.status
						WHEN 'LIVE' THEN 0
						WHEN 'SCHEDULED' THEN 1
						WHEN 'DRAFT' THEN 2
						WHEN 'PAUSED' THEN 3
						WHEN 'SOLD_OUT' THEN 4
						ELSE 5
					END,
					c.id DESC
					""";
			default -> "c.id DESC";
		};
	}

	private static String statusLabel(String status) {
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

	private static String sourceTypeLabel(String sourceType) {
		return switch (sourceType) {
			case "OFFICIAL" -> "官方賞";
			case "SELF_MADE" -> "自製賞";
			case "MIXED" -> "自營混套賞";
			case "BLIND_BOX" -> "盲盒賞";
			case "CARD" -> "卡牌賞";
			case "GK" -> "GK 賞";
			case "PREORDER" -> "預購賞";
			default -> sourceType;
		};
	}
}
