package com.luckybox.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CampaignRepository {

	private final JdbcTemplate jdbcTemplate;

	CampaignRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	CampaignPage findVisibleCampaigns(CampaignQuery query) {
		List<Object> params = new ArrayList<>();
		String whereClause = buildWhereClause(query, params);
		long totalElements = countVisibleCampaigns(whereClause, params);
		int totalPages = query.size() == 0 ? 0 : (int) Math.ceil(totalElements / (double) query.size());

		List<Object> contentParams = new ArrayList<>(params);
		contentParams.add(query.size());
		contentParams.add(query.page() * query.size());
		String sql = """
				WITH available_tickets AS (
					SELECT campaign_id, COUNT(*) AS remaining_tickets
					FROM kuji_tickets
					WHERE status = 'AVAILABLE'
					GROUP BY campaign_id
				)
				SELECT
					c.id, c.slug, c.title, c.subtitle, c.cover_image_url, c.source_type, c.status, c.price_per_draw,
					c.total_tickets, COALESCE(at.remaining_tickets, 0) AS db_remaining_tickets, c.has_last_prize,
					c.age_restricted, c.minimum_age,
					(
						SELECT p.rank || '賞剩 ' || COUNT(t.id)
						FROM prizes p
						JOIN kuji_tickets t ON t.prize_id = p.id AND t.status = 'AVAILABLE'
						WHERE p.campaign_id = c.id
						  AND p.is_last_prize = 0
						GROUP BY p.id, p.rank, p.sort_order
						ORDER BY p.sort_order
						LIMIT 1
					) AS rare_hint
				FROM kuji_campaigns c
				LEFT JOIN available_tickets at ON at.campaign_id = c.id
				""" + whereClause + """
				ORDER BY
				""" + sortClause(query.sort()) + "\n" + """
				LIMIT ? OFFSET ?
				""";
		List<CampaignSummary> content = jdbcTemplate.query(sql, (rs, rowNum) -> {
			int totalTickets = rs.getInt("total_tickets");
			int remainingTickets = rs.getInt("db_remaining_tickets");
			return new CampaignSummary(
					rs.getLong("id"),
					rs.getString("slug"),
					rs.getString("title"),
					rs.getString("subtitle"),
					rs.getString("cover_image_url"),
					rs.getString("source_type"),
					sourceTypeLabel(rs.getString("source_type")),
					rs.getString("status"),
					statusLabel(rs.getString("status")),
					rs.getInt("price_per_draw"),
					totalTickets,
					remainingTickets,
					rs.getInt("has_last_prize") == 1,
					rareHint(rs.getString("rare_hint"), rs.getInt("has_last_prize") == 1),
					CampaignMath.remainingRate(remainingTickets, totalTickets),
					rs.getInt("age_restricted") == 1,
					integerOrNull(rs, "minimum_age"),
					ageRestrictionLabel(rs.getInt("age_restricted") == 1, integerOrNull(rs, "minimum_age")));
		}, contentParams.toArray());

		return new CampaignPage(
				content,
				query.page(),
				query.size(),
				totalElements,
				totalPages,
				query.sort(),
				query.keyword(),
				query.sourceType(),
				query.status());
	}

	Optional<CampaignDetail> findBySlug(String slug) {
		List<CampaignDetail> campaigns = jdbcTemplate.query("""
				SELECT
					id, slug, title, subtitle, description, cover_image_url, banner_image_url,
					source_type, status, price_per_draw,
					total_tickets,
					COALESCE((
						SELECT COUNT(*)
						FROM kuji_tickets t
						WHERE t.campaign_id = kuji_campaigns.id
						  AND t.status = 'AVAILABLE'
					), 0) AS db_remaining_tickets,
					has_last_prize, last_prize_rule,
					shipping_note, return_policy_note, fairness_mode, seed_hash,
					rights_notice, age_restricted, minimum_age, age_verification_note
				FROM kuji_campaigns
				WHERE slug = ?
				""", (rs, rowNum) -> {
			long campaignId = rs.getLong("id");
			int totalTickets = rs.getInt("total_tickets");
			int remainingTickets = rs.getInt("db_remaining_tickets");
			List<PrizeSummary> prizes = findPrizes(campaignId, remainingTickets);
			return new CampaignDetail(
					campaignId,
					rs.getString("slug"),
					rs.getString("title"),
					rs.getString("subtitle"),
					rs.getString("description"),
					rs.getString("cover_image_url"),
					rs.getString("banner_image_url"),
					rs.getString("source_type"),
					sourceTypeLabel(rs.getString("source_type")),
					rs.getString("status"),
					statusLabel(rs.getString("status")),
					rs.getInt("price_per_draw"),
					totalTickets,
					remainingTickets,
					rs.getInt("has_last_prize") == 1,
					rs.getString("last_prize_rule"),
					rs.getString("shipping_note"),
					rs.getString("return_policy_note"),
					rs.getString("fairness_mode"),
					rs.getString("seed_hash"),
					CampaignMath.remainingRate(remainingTickets, totalTickets),
					rightsNotice(rs.getString("rights_notice"), rs.getString("source_type")),
					rs.getInt("age_restricted") == 1,
					integerOrNull(rs, "minimum_age"),
					ageRestrictionLabel(rs.getInt("age_restricted") == 1, integerOrNull(rs, "minimum_age")),
					rs.getString("age_verification_note"),
					prizes);
		}, slug);

		return campaigns.stream().findFirst();
	}

	List<PrizeSummary> findPrizes(long campaignId, int remainingTickets) {
		return jdbcTemplate.query("""
				SELECT
					p.id, p.rank, p.name, p.description, p.original_quantity, p.is_last_prize,
					CASE
						WHEN p.is_last_prize = 1 THEN p.remaining_quantity
						ELSE COALESCE(available.available_quantity, 0)
					END AS db_remaining_quantity
				FROM prizes p
				LEFT JOIN (
					SELECT prize_id, COUNT(*) AS available_quantity
					FROM kuji_tickets
					WHERE campaign_id = ?
					  AND status = 'AVAILABLE'
					GROUP BY prize_id
				) available ON available.prize_id = p.id
				WHERE p.campaign_id = ?
				ORDER BY p.sort_order
				""", (rs, rowNum) -> {
			int remainingQuantity = rs.getInt("db_remaining_quantity");
			boolean lastPrize = rs.getInt("is_last_prize") == 1;
			return new PrizeSummary(
					rs.getLong("id"),
					rs.getString("rank"),
					rs.getString("name"),
					rs.getString("description"),
					rs.getInt("original_quantity"),
					remainingQuantity,
					lastPrize,
					CampaignMath.probability(remainingQuantity, remainingTickets, lastPrize));
		}, campaignId, campaignId);
	}

	private long countVisibleCampaigns(String whereClause, List<Object> params) {
		Long total = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM kuji_campaigns c
				""" + whereClause, Long.class, params.toArray());
		return total == null ? 0 : total;
	}

	private String buildWhereClause(CampaignQuery query, List<Object> params) {
		StringBuilder where = new StringBuilder("WHERE c.status IN ('LIVE', 'SCHEDULED', 'SOLD_OUT')\n");
		if (query.keyword() != null && !query.keyword().isBlank()) {
			String keyword = "%" + query.keyword().toLowerCase() + "%";
			where.append("""
					AND (
						lower(c.title) LIKE ?
						OR lower(c.subtitle) LIKE ?
						OR lower(c.description) LIKE ?
						OR lower(c.brand_name) LIKE ?
					)
					""");
			params.add(keyword);
			params.add(keyword);
			params.add(keyword);
			params.add(keyword);
		}
		if (query.sourceType() != null) {
			where.append("AND c.source_type = ?\n");
			params.add(query.sourceType());
		}
		if (query.status() != null) {
			where.append("AND c.status = ?\n");
			params.add(query.status());
		}
		return where.toString();
	}

	private static String sortClause(String sort) {
		return switch (sort) {
			case "latest" -> "c.id DESC";
			case "popular" -> "(c.total_tickets - db_remaining_tickets) DESC, c.id ASC";
			case "priceAsc" -> "c.price_per_draw ASC, c.id ASC";
			case "priceDesc" -> "c.price_per_draw DESC, c.id ASC";
			case "remainingAsc" -> "db_remaining_tickets ASC, c.id ASC";
			default -> """
					CASE c.status WHEN 'LIVE' THEN 0 WHEN 'SCHEDULED' THEN 1 ELSE 2 END,
					c.id ASC
					""";
		};
	}

	private static String rareHint(String rareHint, boolean hasLastPrize) {
		if (rareHint != null && !rareHint.isBlank()) {
			return rareHint;
		}
		return hasLastPrize ? "最後賞啟用" : "公開剩餘數";
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "LIVE" -> "開抽中";
			case "SCHEDULED" -> "即將開抽";
			case "SOLD_OUT" -> "已完抽";
			case "PAUSED" -> "暫停中";
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

	private static Integer integerOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private static String ageRestrictionLabel(boolean ageRestricted, Integer minimumAge) {
		if (!ageRestricted) {
			return "全年齡";
		}
		if (minimumAge == null || minimumAge <= 0) {
			return "年齡限制";
		}
		return minimumAge + "+";
	}

	private static String rightsNotice(String rightsNotice, String sourceType) {
		if (rightsNotice != null && !rightsNotice.isBlank()) {
			return rightsNotice;
		}
		if ("OFFICIAL".equals(sourceType)) {
			return "官方或授權商品素材已由營運確認，正式公開前仍須保留授權或進貨佐證。";
		}
		return "商品來源與圖片素材由營運確認可於平台展示。";
	}
}
