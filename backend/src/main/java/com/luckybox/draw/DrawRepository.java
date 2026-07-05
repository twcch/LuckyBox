package com.luckybox.draw;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DrawRepository {

	private final JdbcTemplate jdbcTemplate;

	DrawRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<DrawOrderResponse> findOrderByIdempotencyKey(long userId, String idempotencyKey) {
		List<Long> orderIds = jdbcTemplate.query("""
				SELECT id
				FROM draw_orders
				WHERE user_id = ? AND idempotency_key = ?
				""", (rs, rowNum) -> rs.getLong("id"), userId, idempotencyKey);
		return orderIds.stream().findFirst().map(this::findOrderResponse);
	}

	Optional<DrawOrderResponse> findOrderResponse(long userId, long orderId) {
		List<Long> orderIds = jdbcTemplate.query("""
				SELECT id
				FROM draw_orders
				WHERE id = ? AND user_id = ?
				""", (rs, rowNum) -> rs.getLong("id"), orderId, userId);
		return orderIds.stream().findFirst().map(this::findOrderResponse);
	}

	Optional<DrawResultResponse> findResultResponse(long userId, long resultId) {
		List<DrawResultResponse> results = jdbcTemplate.query("""
				SELECT
					r.id, t.serial_number, p.rank, p.name, p.is_last_prize
				FROM draw_results r
				JOIN kuji_tickets t ON t.id = r.ticket_id
				JOIN prizes p ON p.id = r.prize_id
				WHERE r.id = ? AND r.user_id = ?
				""", (rs, rowNum) -> new DrawResultResponse(
				rs.getLong("id"),
				rs.getString("serial_number"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getInt("is_last_prize") == 1), resultId, userId);
		return results.stream().findFirst();
	}

	void ensureWallet(long userId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT OR IGNORE INTO wallets (
					user_id, cash_point_balance, bonus_point_balance, locked_balance, created_at, updated_at
				)
				VALUES (?, 0, 0, 0, ?, ?)
				""", userId, now, now);
	}

	Optional<CampaignRow> findCampaignForDraw(String slug) {
		List<CampaignRow> campaigns = jdbcTemplate.query("""
				SELECT id, slug, title, status, price_per_draw, remaining_tickets, has_last_prize, fairness_mode
				FROM kuji_campaigns
				WHERE slug = ?
				""", (rs, rowNum) -> new CampaignRow(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				rs.getString("status"),
				rs.getInt("price_per_draw"),
				rs.getInt("remaining_tickets"),
				rs.getInt("has_last_prize") == 1,
				rs.getString("fairness_mode")), slug);
		return campaigns.stream().findFirst();
	}

	String findServerSeed(long campaignId) {
		return jdbcTemplate.query(
				"SELECT server_seed FROM kuji_campaigns WHERE id = ?",
				rs -> rs.next() ? rs.getString("server_seed") : null,
				campaignId);
	}

	void commitSeedIfAbsent(long campaignId, String serverSeed, String seedHash) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET server_seed = ?, seed_hash = ?, updated_at = ?
				WHERE id = ? AND server_seed IS NULL
				""", serverSeed, seedHash, now, campaignId);
	}

	void revealSeed(long campaignId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET revealed_seed = server_seed, updated_at = ?
				WHERE id = ? AND server_seed IS NOT NULL AND revealed_seed IS NULL
				""", now, campaignId);
	}

	int countAvailableTickets(long campaignId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM kuji_tickets WHERE campaign_id = ? AND status = 'AVAILABLE'",
				Integer.class, campaignId);
		return count == null ? 0 : count;
	}

	TicketRow selectAvailableTicketByOffset(long campaignId, int offset) {
		List<TicketRow> rows = jdbcTemplate.query("""
				SELECT
					t.id, t.prize_id, t.serial_number,
					p.rank, p.name, p.is_last_prize
				FROM kuji_tickets t
				JOIN prizes p ON p.id = t.prize_id
				WHERE t.campaign_id = ? AND t.status = 'AVAILABLE'
				ORDER BY t.id
				LIMIT 1 OFFSET ?
				""", (rs, rowNum) -> new TicketRow(
				rs.getLong("id"),
				rs.getLong("prize_id"),
				rs.getString("serial_number"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getInt("is_last_prize") == 1), campaignId, offset);
		return rows.stream().findFirst().orElse(null);
	}

	WalletBalance walletBalance(long userId) {
		return jdbcTemplate.queryForObject("""
				SELECT id, cash_point_balance, bonus_point_balance, locked_balance
				FROM wallets
				WHERE user_id = ?
				""", (rs, rowNum) -> new WalletBalance(
				rs.getLong("id"),
				rs.getInt("cash_point_balance"),
				rs.getInt("bonus_point_balance"),
				rs.getInt("locked_balance")), userId);
	}

	long createDrawOrder(
			long userId,
			CampaignRow campaign,
			int quantity,
			int pointSpent,
			int originalPointSpent,
			int discountAmount,
			Long couponId,
			String idempotencyKey) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_orders (
					user_id, campaign_id, quantity, point_spent, original_point_spent,
					discount_amount, coupon_id, status, idempotency_key,
					client_request_id, ip_address, user_agent, created_at, completed_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, NULL, NULL, NULL, ?, NULL)
				""",
				userId,
				campaign.id(),
				quantity,
				pointSpent,
				originalPointSpent,
				discountAmount,
				couponId,
				idempotencyKey,
				now);
		Long orderId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return orderId == null ? 0 : orderId;
	}

	Optional<CouponForDraw> findCouponByCode(String code) {
		List<CouponForDraw> coupons = jdbcTemplate.query("""
				SELECT id, code, type, vip_tier, value, min_spend, usage_limit, used_count, starts_at, ends_at, status
				FROM coupons
				WHERE upper(code) = ?
				""", (rs, rowNum) -> {
			Integer usageLimit = rs.getObject("usage_limit") == null ? null : rs.getInt("usage_limit");
			return new CouponForDraw(
					rs.getLong("id"),
					rs.getString("code"),
					rs.getString("type"),
					rs.getString("vip_tier"),
					rs.getInt("value"),
					rs.getInt("min_spend"),
					usageLimit,
					rs.getInt("used_count"),
					rs.getString("starts_at"),
					rs.getString("ends_at"),
					rs.getString("status"));
		}, code);
		return coupons.stream().findFirst();
	}

	boolean hasUserUsedCoupon(long userId, long couponId) {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM coupon_usages
				WHERE user_id = ? AND coupon_id = ? AND status = 'APPLIED'
				""", Integer.class, userId, couponId);
		return count != null && count > 0;
	}

	boolean incrementCouponUsedCount(long couponId) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE coupons
				SET used_count = used_count + 1, updated_at = ?
				WHERE id = ?
				  AND status = 'ACTIVE'
				  AND (starts_at IS NULL OR starts_at <= ?)
				  AND (ends_at IS NULL OR ends_at >= ?)
				  AND (usage_limit IS NULL OR used_count < usage_limit)
				""", now, couponId, now, now);
		return rows == 1;
	}

	void createCouponUsage(long userId, long couponId, long drawOrderId, int discountAmount) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO coupon_usages (
					coupon_id, user_id, reference_type, reference_id, discount_amount, point_amount, status, used_at
				)
				VALUES (?, ?, 'DrawOrder', ?, ?, 0, 'APPLIED', ?)
				""", couponId, userId, drawOrderId, discountAmount, now);
	}

	List<TicketRow> findRandomAvailableTickets(long campaignId, int quantity) {
		return jdbcTemplate.query("""
				SELECT
					t.id, t.prize_id, t.serial_number,
					p.rank, p.name, p.is_last_prize
				FROM kuji_tickets t
				JOIN prizes p ON p.id = t.prize_id
				WHERE t.campaign_id = ? AND t.status = 'AVAILABLE'
				ORDER BY random()
				LIMIT ?
				""", (rs, rowNum) -> new TicketRow(
				rs.getLong("id"),
				rs.getLong("prize_id"),
				rs.getString("serial_number"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getInt("is_last_prize") == 1), campaignId, quantity);
	}

	boolean markTicketDrawn(TicketRow ticket, long orderId, long userId) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE kuji_tickets
				SET status = 'DRAWN', draw_id = ?, drawn_by_user_id = ?, drawn_at = ?, updated_at = ?
				WHERE id = ? AND status = 'AVAILABLE'
				""", orderId, userId, now, now, ticket.id());
		return rows == 1;
	}

	boolean decrementPrize(long prizeId) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE prizes
				SET remaining_quantity = remaining_quantity - 1, updated_at = ?
				WHERE id = ? AND remaining_quantity > 0
				""", now, prizeId);
		return rows == 1;
	}

	boolean decrementCampaign(long campaignId, int quantity) {
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE kuji_campaigns
				SET remaining_tickets = remaining_tickets - ?,
					status = CASE WHEN remaining_tickets - ? = 0 THEN 'SOLD_OUT' ELSE status END,
					updated_at = ?
				WHERE id = ?
				  AND status = 'LIVE'
				  AND remaining_tickets >= ?
				""", quantity, quantity, now, campaignId, quantity);
		return rows == 1;
	}

	int currentRemainingTickets(long campaignId) {
		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT remaining_tickets FROM kuji_campaigns WHERE id = ?", Integer.class, campaignId);
		return remaining == null ? -1 : remaining;
	}

	Optional<LastPrizeRow> findAvailableLastPrize(long campaignId) {
		List<LastPrizeRow> rows = jdbcTemplate.query("""
				SELECT id, rank, name
				FROM prizes
				WHERE campaign_id = ? AND is_last_prize = 1 AND remaining_quantity > 0
				ORDER BY id
				LIMIT 1
				""", (rs, rowNum) -> new LastPrizeRow(
				rs.getLong("id"),
				rs.getString("rank"),
				rs.getString("name")), campaignId);
		return rows.stream().findFirst();
	}

	TicketRow createLastPrizeTicket(CampaignRow campaign, LastPrizeRow prize, long orderId, long userId) {
		String now = Instant.now().toString();
		String serial = campaign.slug().toUpperCase().replace('-', '_') + "-LAST-" + orderId;
		jdbcTemplate.update("""
				INSERT INTO kuji_tickets (
					campaign_id, prize_id, serial_number, status, draw_id, drawn_by_user_id, drawn_at, created_at, updated_at
				)
				VALUES (?, ?, ?, 'DRAWN', ?, ?, ?, ?, ?)
				""", campaign.id(), prize.id(), serial, orderId, userId, now, now, now);
		Long ticketId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return new TicketRow(ticketId == null ? 0 : ticketId, prize.id(), serial, prize.rank(), prize.name(), true);
	}

	boolean deductBonusPoints(long userId, long walletId, long drawOrderId, int amount) {
		if (amount <= 0) {
			return true;
		}
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE wallets
				SET bonus_point_balance = bonus_point_balance - ?, updated_at = ?
				WHERE id = ? AND user_id = ? AND bonus_point_balance >= ?
				""", amount, now, walletId, userId, amount);
		if (rows != 1) {
			return false;
		}
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE id = ? AND user_id = ?",
				Integer.class,
				walletId,
				userId);
		insertLedger(userId, walletId, "DRAW_SPEND", -amount, "BONUS", balanceAfter, drawOrderId, "抽賞扣贈點", now);
		return true;
	}

	boolean deductCashPoints(long userId, long walletId, long drawOrderId, int amount) {
		if (amount <= 0) {
			return true;
		}
		String now = Instant.now().toString();
		int rows = jdbcTemplate.update("""
				UPDATE wallets
				SET cash_point_balance = cash_point_balance - ?, updated_at = ?
				WHERE id = ? AND user_id = ? AND cash_point_balance >= ?
				""", amount, now, walletId, userId, amount);
		if (rows != 1) {
			return false;
		}
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT cash_point_balance FROM wallets WHERE id = ? AND user_id = ?",
				Integer.class,
				walletId,
				userId);
		insertLedger(userId, walletId, "DRAW_SPEND", -amount, "CASH", balanceAfter, drawOrderId, "抽賞扣現金點", now);
		return true;
	}

	long createDrawResult(long userId, CampaignRow campaign, long orderId, TicketRow ticket, int resultIndex) {
		return createDrawResult(userId, campaign, orderId, ticket, resultIndex, "server-random:" + ticket.serialNumber());
	}

	long createDrawResult(
			long userId, CampaignRow campaign, long orderId, TicketRow ticket, int resultIndex, String randomProof) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO draw_results (
					draw_order_id, ticket_id, prize_id, user_id, campaign_id, result_index, random_proof, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				orderId,
				ticket.id(),
				ticket.prizeId(),
				userId,
				campaign.id(),
				resultIndex,
				randomProof,
				now);
		Long resultId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
		return resultId == null ? 0 : resultId;
	}

	void createUserPrize(long userId, CampaignRow campaign, TicketRow ticket, long drawResultId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				INSERT INTO user_prizes (
					user_id, campaign_id, prize_id, draw_result_id, status, shipment_id, expires_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, 'IN_BOX', NULL, NULL, ?, ?)
				""", userId, campaign.id(), ticket.prizeId(), drawResultId, now, now);
	}

	void completeDrawOrder(long orderId) {
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE draw_orders
				SET status = 'COMPLETED', completed_at = ?
				WHERE id = ?
				""", now, orderId);
	}

	DrawOrderResponse findOrderResponse(long orderId) {
		DrawOrderRow order = jdbcTemplate.queryForObject("""
				SELECT
					d.id, c.slug, c.title, d.quantity,
					COALESCE(d.original_point_spent, d.point_spent) AS original_point_spent,
					COALESCE(d.discount_amount, 0) AS discount_amount,
					d.point_spent,
					cp.code AS coupon_code,
					d.status, d.completed_at,
					COALESCE(w.cash_point_balance, 0) + COALESCE(w.bonus_point_balance, 0) - COALESCE(w.locked_balance, 0)
						AS balance_after
				FROM draw_orders d
				JOIN kuji_campaigns c ON c.id = d.campaign_id
				LEFT JOIN wallets w ON w.user_id = d.user_id
				LEFT JOIN coupons cp ON cp.id = d.coupon_id
				WHERE d.id = ?
				""", (rs, rowNum) -> mapOrder(rs), orderId);
		List<DrawResultResponse> results = jdbcTemplate.query("""
				SELECT
					r.id, t.serial_number, p.rank, p.name, p.is_last_prize
				FROM draw_results r
				JOIN kuji_tickets t ON t.id = r.ticket_id
				JOIN prizes p ON p.id = r.prize_id
				WHERE r.draw_order_id = ?
				ORDER BY r.result_index
				""", (rs, rowNum) -> new DrawResultResponse(
				rs.getLong("id"),
				rs.getString("serial_number"),
				rs.getString("rank"),
				rs.getString("name"),
				rs.getInt("is_last_prize") == 1), orderId);
		return new DrawOrderResponse(
				order.id(),
				order.campaignSlug(),
				order.campaignTitle(),
				order.quantity(),
				order.originalPointSpent(),
				order.discountAmount(),
				order.pointSpent(),
				order.couponCode(),
				order.status(),
				order.balanceAfter(),
				order.completedAt(),
				results);
	}

	int totalDrawSpend(long userId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(point_spent), 0) FROM draw_orders WHERE user_id = ? AND status = 'COMPLETED'",
				Integer.class, userId);
		return total == null ? 0 : total;
	}

	void addSpendThresholdBonus(long userId, long walletId, long drawOrderId, int amount) {
		if (amount <= 0) {
			return;
		}
		String now = Instant.now().toString();
		jdbcTemplate.update("""
				UPDATE wallets
				SET bonus_point_balance = bonus_point_balance + ?, updated_at = ?
				WHERE id = ? AND user_id = ?
				""", amount, now, walletId, userId);
		Integer balanceAfter = jdbcTemplate.queryForObject(
				"SELECT bonus_point_balance FROM wallets WHERE id = ? AND user_id = ?",
				Integer.class,
				walletId,
				userId);
		insertLedger(userId, walletId, "SPEND_THRESHOLD_BONUS", amount, "BONUS", balanceAfter, drawOrderId, "消費門檻紅利", now);
	}

	private void insertLedger(
			long userId,
			long walletId,
			String type,
			int amount,
			String pointKind,
			Integer balanceAfter,
			long drawOrderId,
			String reason,
			String now) {
		jdbcTemplate.update("""
				INSERT INTO wallet_ledger (
					user_id, wallet_id, type, amount, point_kind, balance_after,
					reference_type, reference_id, reason, created_by, created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, 'DrawOrder', ?, ?, ?, ?)
				""",
				userId,
				walletId,
				type,
				amount,
				pointKind,
				balanceAfter == null ? 0 : balanceAfter,
				drawOrderId,
				reason,
				userId,
				now);
	}

	private static DrawOrderRow mapOrder(ResultSet rs) throws SQLException {
		return new DrawOrderRow(
				rs.getLong("id"),
				rs.getString("slug"),
				rs.getString("title"),
				rs.getInt("quantity"),
				rs.getInt("original_point_spent"),
				rs.getInt("discount_amount"),
				rs.getInt("point_spent"),
				rs.getString("coupon_code"),
				rs.getString("status"),
				rs.getInt("balance_after"),
				rs.getString("completed_at"));
	}

	record CampaignRow(
			long id,
			String slug,
			String title,
			String status,
			int pricePerDraw,
			int remainingTickets,
			boolean hasLastPrize,
			String fairnessMode) {
	}

	record TicketRow(long id, long prizeId, String serialNumber, String prizeRank, String prizeName, boolean lastPrize) {
	}

	record LastPrizeRow(long id, String rank, String name) {
	}

	record WalletBalance(long walletId, int cashPointBalance, int bonusPointBalance, int lockedBalance) {

		int availableBalance() {
			return cashPointBalance + bonusPointBalance - lockedBalance;
		}
	}

	record CouponForDraw(
			long id,
			String code,
			String type,
			String vipTier,
			int value,
			int minSpend,
			Integer usageLimit,
			int usedCount,
			String startsAt,
			String endsAt,
			String status) {
	}

	private record DrawOrderRow(
			long id,
			String campaignSlug,
			String campaignTitle,
			int quantity,
			int originalPointSpent,
			int discountAmount,
			int pointSpent,
			String couponCode,
			String status,
			int balanceAfter,
			String completedAt) {
	}
}
