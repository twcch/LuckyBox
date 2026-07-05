package com.luckybox.draw;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.fairness.Fairness;
import com.luckybox.vip.VipService;
import com.luckybox.vip.VipTiers;

@Service
class DrawService {

	/**
	 * In-process serialization avoids SQLite read-then-write contention (SQLITE_BUSY) on a single
	 * instance. Correctness — no oversell and no duplicate draw — does NOT depend on this lock: it
	 * rests on the conditional UPDATEs ({@code markTicketDrawn} / {@code decrementPrize} /
	 * {@code decrementCampaign} all check affected-row counts) together with the UNIQUE constraint on
	 * {@code draw_results.ticket_id}. A multi-instance deployment would require a database that
	 * supports row locking (e.g. Postgres {@code SELECT ... FOR UPDATE}) and can disable this flag.
	 */
	private final Object drawLock = new Object();
	private final boolean inProcessSerialization;
	private final int spendThreshold;
	private final int spendThresholdBonus;
	private final DrawRepository drawRepository;
	private final AuditLogHelper auditLogHelper;
	private final VipService vipService;

	DrawService(
			DrawRepository drawRepository,
			AuditLogHelper auditLogHelper,
			VipService vipService,
			@Value("${luckybox.draw.in-process-serialization:true}") boolean inProcessSerialization,
			@Value("${luckybox.promo.spend-threshold:0}") int spendThreshold,
			@Value("${luckybox.promo.spend-threshold-bonus:0}") int spendThresholdBonus) {
		this.drawRepository = drawRepository;
		this.auditLogHelper = auditLogHelper;
		this.vipService = vipService;
		this.inProcessSerialization = inProcessSerialization;
		this.spendThreshold = Math.max(0, spendThreshold);
		this.spendThresholdBonus = Math.max(0, spendThresholdBonus);
	}

	@Transactional
	DrawOrderResponse createDrawOrder(CreateDrawOrderRequest request) {
		if (!inProcessSerialization) {
			return createDrawOrderLocked(request);
		}
		synchronized (drawLock) {
			return createDrawOrderLocked(request);
		}
	}

	DrawOrderResponse findDrawOrder(long orderId) {
		long userId = currentUserId();
		return drawRepository.findOrderResponse(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DRAW_ORDER_NOT_FOUND", "找不到指定抽賞訂單。"));
	}

	DrawResultResponse findDrawResult(long resultId) {
		long userId = currentUserId();
		return drawRepository.findResultResponse(userId, resultId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DRAW_RESULT_NOT_FOUND", "找不到指定抽賞結果。"));
	}

	private DrawOrderResponse createDrawOrderLocked(CreateDrawOrderRequest request) {
		long userId = currentUserId();
		return drawRepository.findOrderByIdempotencyKey(userId, request.idempotencyKey())
				.orElseGet(() -> createNewDrawOrder(userId, request));
	}

	private DrawOrderResponse createNewDrawOrder(long userId, CreateDrawOrderRequest request) {
		DrawRepository.CampaignRow campaign = drawRepository.findCampaignForDraw(request.campaignSlug())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。"));
		if (!"LIVE".equals(campaign.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_NOT_LIVE", "此賞池目前不可抽。");
		}
		if (campaign.remainingTickets() < request.quantity()) {
			throw new ApiException(HttpStatus.CONFLICT, "DRAW_REMAINING_NOT_ENOUGH", "剩餘籤數不足。");
		}

		int originalPointSpent = campaign.pricePerDraw() * request.quantity();
		CouponApplication coupon = resolveDrawCoupon(userId, request.couponCode(), originalPointSpent);
		int pointSpent = DrawPriceCalculator.finalPointSpent(originalPointSpent, coupon.discountAmount());
		drawRepository.ensureWallet(userId);
		DrawRepository.WalletBalance wallet = drawRepository.walletBalance(userId);
		if (wallet.availableBalance() < pointSpent) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "點數餘額不足。");
		}

		long orderId;
		try {
			orderId = drawRepository.createDrawOrder(
					userId,
					campaign,
					request.quantity(),
					pointSpent,
					originalPointSpent,
					coupon.discountAmount(),
					coupon.couponId(),
					request.idempotencyKey());
		} catch (DataAccessException ex) {
			// idempotency_key is globally UNIQUE; a key already taken (e.g. reused across users) must not
			// surface as a 500. Same-user retries never reach here (findOrderByIdempotencyKey returns first).
			// SQLite sets no standard SQLState, so the violation arrives as UncategorizedSQLException — match
			// on the constraint name like AdminCampaignRepository does for slug conflicts.
			if (ex.getMessage() != null && ex.getMessage().contains("idempotency_key")) {
				throw new ApiException(HttpStatus.CONFLICT, "DRAW_IDEMPOTENCY_CONFLICT",
						"請求識別碼重複，請使用新的識別碼重試。");
			}
			throw ex;
		}
		recordCouponUsage(userId, orderId, coupon);

		deductPoints(userId, wallet, orderId, pointSpent);
		if ("HASH_COMMIT_REVEAL".equals(campaign.fairnessMode())) {
			drawCommitReveal(userId, campaign, orderId, request.quantity());
		} else {
			drawServerRandom(userId, campaign, orderId, request.quantity());
		}
		if (!drawRepository.decrementCampaign(campaign.id(), request.quantity())) {
			throw new ApiException(HttpStatus.CONFLICT, "DRAW_REMAINING_NOT_ENOUGH", "剩餘籤數不足。");
		}
		if (drawRepository.currentRemainingTickets(campaign.id()) == 0) {
			drawRepository.revealSeed(campaign.id());
			awardLastPrize(userId, campaign, orderId, request.quantity());
		}
		drawRepository.completeDrawOrder(orderId);
		awardSpendThresholdBonus(userId, wallet.walletId(), orderId, pointSpent);
		auditLogHelper.recordSystemAction(
				"DRAW_ORDER_COMPLETED",
				"DrawOrder",
				String.valueOf(orderId),
				"{\"userId\":" + userId + ",\"campaign\":\"" + campaign.slug() + "\",\"quantity\":" + request.quantity()
						+ ",\"pointSpent\":" + pointSpent + ",\"discountAmount\":" + coupon.discountAmount()
						+ ",\"couponCode\":" + quotedOrNull(coupon.code()) + "}");
		return drawRepository.findOrderResponse(orderId);
	}

	private CouponApplication resolveDrawCoupon(long userId, String couponCode, int originalPointSpent) {
		String normalizedCode = normalizeCouponCode(couponCode);
		if (normalizedCode == null) {
			return CouponApplication.none();
		}
		DrawRepository.CouponForDraw coupon = drawRepository.findCouponByCode(normalizedCode)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_FOUND", "找不到指定優惠券。"));
		if (!"ACTIVE".equals(coupon.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_ACTIVE", "此優惠券目前不可使用。");
		}
		Instant now = Instant.now();
		if (coupon.startsAt() != null && Instant.parse(coupon.startsAt()).isAfter(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_STARTED", "此優惠券尚未開始。");
		}
		if (coupon.endsAt() != null && Instant.parse(coupon.endsAt()).isBefore(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "此優惠券已過期。");
		}
		if (!"DISCOUNT".equals(coupon.type())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_APPLICABLE", "此優惠券不可用於抽賞扣點。");
		}
		if (!VipTiers.isEligible(vipService.status().tier(), coupon.vipTier())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "COUPON_VIP_REQUIRED", "此優惠券限 VIP 會員使用。");
		}
		if (originalPointSpent < coupon.minSpend()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_MIN_SPEND_NOT_REACHED", "本次抽賞未達優惠券最低使用門檻。");
		}
		if (coupon.usageLimit() != null && coupon.usedCount() >= coupon.usageLimit()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "此優惠券已達使用上限。");
		}
		if (drawRepository.hasUserUsedCoupon(userId, coupon.id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "此優惠券已使用過。");
		}
		int discountAmount = DrawPriceCalculator.discountAmount(coupon.value(), originalPointSpent);
		return new CouponApplication(coupon.id(), coupon.code(), discountAmount);
	}

	private void recordCouponUsage(long userId, long orderId, CouponApplication coupon) {
		if (!coupon.applied()) {
			return;
		}
		if (!drawRepository.incrementCouponUsedCount(coupon.couponId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "此優惠券已達使用上限。");
		}
		drawRepository.createCouponUsage(userId, coupon.couponId(), orderId, coupon.discountAmount());
	}

	private static String normalizeCouponCode(String couponCode) {
		if (couponCode == null || couponCode.isBlank()) {
			return null;
		}
		String normalized = couponCode.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z0-9][A-Z0-9_-]{2,31}")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_CODE", "優惠券代碼格式不正確。");
		}
		return normalized;
	}

	private static String quotedOrNull(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private void drawServerRandom(long userId, DrawRepository.CampaignRow campaign, long orderId, int quantity) {
		List<DrawRepository.TicketRow> tickets = drawRepository.findRandomAvailableTickets(campaign.id(), quantity);
		if (tickets.size() < quantity) {
			throw new ApiException(HttpStatus.CONFLICT, "DRAW_REMAINING_NOT_ENOUGH", "剩餘籤數不足。");
		}
		for (int index = 0; index < tickets.size(); index++) {
			DrawRepository.TicketRow ticket = tickets.get(index);
			markAndRecord(userId, campaign, orderId, ticket, index + 1, "server-random:" + ticket.serialNumber());
		}
	}

	/**
	 * Provably-fair selection for HASH_COMMIT_REVEAL campaigns: each pick derives its ticket from
	 * HMAC-SHA256(serverSeed, "{orderId}:{index}") modulo the live available count, and records that
	 * HMAC as the draw's random_proof. Once the seed is revealed at sell-out anyone can recompute it.
	 */
	private void drawCommitReveal(long userId, DrawRepository.CampaignRow campaign, long orderId, int quantity) {
		String serverSeed = ensureCommittedSeed(campaign);
		for (int index = 1; index <= quantity; index++) {
			int available = drawRepository.countAvailableTickets(campaign.id());
			if (available <= 0) {
				throw new ApiException(HttpStatus.CONFLICT, "DRAW_REMAINING_NOT_ENOUGH", "剩餘籤數不足。");
			}
			String nonce = orderId + ":" + index;
			String hmac = Fairness.hmacSha256Hex(serverSeed, nonce);
			int offset = Fairness.selectionIndex(hmac, available);
			DrawRepository.TicketRow ticket = drawRepository.selectAvailableTicketByOffset(campaign.id(), offset);
			if (ticket == null) {
				throw new ApiException(HttpStatus.CONFLICT, "DRAW_REMAINING_NOT_ENOUGH", "剩餘籤數不足。");
			}
			markAndRecord(userId, campaign, orderId, ticket, index, "hmac-sha256:" + nonce + ":" + hmac);
		}
	}

	private void markAndRecord(
			long userId, DrawRepository.CampaignRow campaign, long orderId,
			DrawRepository.TicketRow ticket, int resultIndex, String randomProof) {
		if (!drawRepository.markTicketDrawn(ticket, orderId, userId) || !drawRepository.decrementPrize(ticket.prizeId())) {
			throw new ApiException(HttpStatus.CONFLICT, "DRAW_TICKET_CONFLICT", "抽賞票券狀態已變更，請重新抽賞。");
		}
		long resultId = drawRepository.createDrawResult(userId, campaign, orderId, ticket, resultIndex, randomProof);
		drawRepository.createUserPrize(userId, campaign, ticket, resultId);
	}

	/** Returns the committed server seed, lazily committing one if the campaign reached LIVE without publish. */
	private String ensureCommittedSeed(DrawRepository.CampaignRow campaign) {
		String seed = drawRepository.findServerSeed(campaign.id());
		if (seed != null) {
			return seed;
		}
		String newSeed = Fairness.newServerSeed();
		drawRepository.commitSeedIfAbsent(campaign.id(), newSeed, Fairness.sha256Hex(newSeed));
		return drawRepository.findServerSeed(campaign.id());
	}

	/**
	 * Kuji-style "last prize" (最後賞): when this order sells the campaign out, the same buyer additionally
	 * receives the last-prize. It has no ticket in the random pool (see {@code DevSeedRunner}), so a DRAWN
	 * ticket is synthesized for it. Awarding is guarded by {@code decrementPrize} (conditional on
	 * remaining_quantity {@literal >} 0), so at most one order can ever win it. Caller ensures sell-out.
	 */
	private void awardLastPrize(
			long userId, DrawRepository.CampaignRow campaign, long orderId, int regularResultCount) {
		if (!campaign.hasLastPrize()) {
			return;
		}
		drawRepository.findAvailableLastPrize(campaign.id()).ifPresent(lastPrize -> {
			if (!drawRepository.decrementPrize(lastPrize.id())) {
				return;
			}
			DrawRepository.TicketRow lastTicket =
					drawRepository.createLastPrizeTicket(campaign, lastPrize, orderId, userId);
			long resultId = drawRepository.createDrawResult(
					userId, campaign, orderId, lastTicket, regularResultCount + 1, "last-prize:" + lastTicket.serialNumber());
			drawRepository.createUserPrize(userId, campaign, lastTicket, resultId);
			auditLogHelper.recordSystemAction(
					"DRAW_LAST_PRIZE_AWARDED",
					"DrawOrder",
					String.valueOf(orderId),
					"{\"userId\":" + userId + ",\"campaign\":\"" + campaign.slug() + "\",\"prizeId\":" + lastPrize.id() + "}");
		});
	}

	/** 消費門檻紅利: grant a one-time bonus the moment cumulative draw spend first crosses the threshold. */
	private void awardSpendThresholdBonus(long userId, long walletId, long orderId, int pointSpent) {
		if (spendThreshold <= 0 || spendThresholdBonus <= 0 || pointSpent <= 0) {
			return;
		}
		int totalAfter = drawRepository.totalDrawSpend(userId);
		int totalBefore = totalAfter - pointSpent;
		if (totalBefore < spendThreshold && totalAfter >= spendThreshold) {
			drawRepository.addSpendThresholdBonus(userId, walletId, orderId, spendThresholdBonus);
			auditLogHelper.recordSystemAction(
					"SPEND_THRESHOLD_BONUS_GRANTED",
					"DrawOrder",
					String.valueOf(orderId),
					"{\"userId\":" + userId + ",\"threshold\":" + spendThreshold + ",\"bonus\":" + spendThresholdBonus + "}");
		}
	}

	private void deductPoints(long userId, DrawRepository.WalletBalance wallet, long orderId, int pointSpent) {
		int bonusSpend = Math.min(wallet.bonusPointBalance(), pointSpent);
		int cashSpend = pointSpent - bonusSpend;
		// Both deductions are guarded conditional UPDATEs; if either affects 0 rows the balance changed
		// under us (lost-update race), so fail the whole transaction rather than silently grant a free draw.
		if (!drawRepository.deductBonusPoints(userId, wallet.walletId(), orderId, bonusSpend)
				|| !drawRepository.deductCashPoints(userId, wallet.walletId(), orderId, cashSpend)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "點數餘額不足。");
		}
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}

	private record CouponApplication(Long couponId, String code, int discountAmount) {

		private static CouponApplication none() {
			return new CouponApplication(null, null, 0);
		}

		private boolean applied() {
			return couponId != null;
		}
	}
}
