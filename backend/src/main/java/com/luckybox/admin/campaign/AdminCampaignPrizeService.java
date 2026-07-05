package com.luckybox.admin.campaign;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
class AdminCampaignPrizeService {

	private static final Set<String> PRIZE_LOCK_STATUSES = Set.of("LIVE", "PAUSED", "SOLD_OUT", "ENDED");
	private static final Set<String> TICKET_STATUSES = Set.of("AVAILABLE", "DRAWN", "VOIDED");

	private final AdminCampaignPrizeRepository adminCampaignPrizeRepository;
	private final AuditLogHelper auditLogHelper;

	AdminCampaignPrizeService(AdminCampaignPrizeRepository adminCampaignPrizeRepository, AuditLogHelper auditLogHelper) {
		this.adminCampaignPrizeRepository = adminCampaignPrizeRepository;
		this.auditLogHelper = auditLogHelper;
	}

	AdminCampaignPrizeOverviewResponse prizes(long campaignId) {
		requireAdmin();
		requireCampaign(campaignId);
		return adminCampaignPrizeRepository.overview(campaignId);
	}

	java.util.List<AdminCampaignTicketResponse> tickets(long campaignId, String status, String keyword, int limit) {
		requireAdmin();
		requireCampaign(campaignId);
		String normalizedStatus = normalizeOptional(status);
		if (normalizedStatus != null && !TICKET_STATUSES.contains(normalizedStatus)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_STATUS", "不支援的 ticket 狀態。");
		}
		return adminCampaignPrizeRepository.tickets(
				campaignId,
				normalizedStatus,
				normalizeOptional(keyword),
				Math.max(1, Math.min(limit, 1000)));
	}

	@Transactional(noRollbackFor = ApiException.class)
	AdminCampaignPrizeResponse createPrize(long campaignId, AdminCampaignPrizeRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignPrizeRepository.CampaignRow campaign = requireCampaign(campaignId);
		rejectPrizeChangeWhenLocked(admin, campaign, "ADMIN_PRIZE_CHANGE_BLOCKED", "Campaign", String.valueOf(campaignId));
		validateRequest(request, 0, 0);
		long prizeId = adminCampaignPrizeRepository.createPrize(campaignId, request);
		adminCampaignPrizeRepository.syncCampaignFromPrizesWhenNoTickets(campaignId);
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_PRIZE_CREATED",
				"Prize",
				String.valueOf(prizeId),
				"{\"campaignId\":" + campaignId + ",\"rank\":\"" + request.rank().trim() + "\"}");
		return adminCampaignPrizeRepository.findPrize(campaignId, prizeId);
	}

	@Transactional(noRollbackFor = ApiException.class)
	AdminCampaignPrizeResponse updatePrize(long campaignId, long prizeId, AdminCampaignPrizeRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignPrizeRepository.CampaignRow campaign = requireCampaign(campaignId);
		AdminCampaignPrizeResponse current = adminCampaignPrizeRepository.findPrize(campaignId, prizeId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PRIZE_NOT_FOUND", "找不到指定獎項。");
		}
		rejectPrizeChangeWhenLocked(admin, campaign, "ADMIN_PRIZE_CHANGE_BLOCKED", "Prize", String.valueOf(prizeId));
		int generatedTickets = adminCampaignPrizeRepository.generatedTickets(campaignId, prizeId);
		int drawnTickets = adminCampaignPrizeRepository.drawnTickets(campaignId, prizeId);
		int availableTickets = adminCampaignPrizeRepository.availableTickets(campaignId, prizeId);
		validateRequest(request, generatedTickets, drawnTickets);
		if (request.lastPrize() && generatedTickets > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_PRIZE_TICKETS_EXIST", "已有 ticket 的獎項不可改為最後賞。");
		}
		// remaining_quantity must equal the real AVAILABLE ticket count. Tickets not yet generated for a
		// raised originalQuantity are NOT drawable, so don't pre-count them here — generateTickets() runs
		// syncPrizeRemainingQuantities() afterwards to bring this up to the new available count.
		int remainingQuantity = request.lastPrize() ? request.originalQuantity() : availableTickets;
		if (!adminCampaignPrizeRepository.updatePrize(campaignId, prizeId, request, remainingQuantity)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PRIZE_NOT_FOUND", "找不到指定獎項。");
		}
		adminCampaignPrizeRepository.syncCampaignFromPrizesWhenNoTickets(campaignId);
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_PRIZE_UPDATED",
				"Prize",
				String.valueOf(prizeId),
				"{\"campaignId\":" + campaignId + ",\"rank\":\"" + request.rank().trim() + "\"}");
		return adminCampaignPrizeRepository.findPrize(campaignId, prizeId);
	}

	@Transactional(noRollbackFor = ApiException.class)
	AdminTicketGenerationResponse generateTickets(long campaignId) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignPrizeRepository.CampaignRow campaign = requireCampaign(campaignId);
		rejectPrizeChangeWhenLocked(admin, campaign, "ADMIN_TICKETS_GENERATION_BLOCKED", "Campaign", String.valueOf(campaignId));
		if (adminCampaignPrizeRepository.nonLastPrizeCount(campaignId) == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PRIZES_REQUIRED", "產生 ticket 前至少需要一個非最後賞獎項。");
		}
		for (AdminCampaignPrizeResponse prize : adminCampaignPrizeRepository.prizes(campaignId)) {
			if (!prize.lastPrize() && prize.generatedTickets() > prize.originalQuantity()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "PRIZE_QUANTITY_BELOW_TICKETS", "獎項數量不可小於已產生 ticket 數。");
			}
		}
		int generatedCount = adminCampaignPrizeRepository.generateMissingTickets(campaign);
		int totalTickets = adminCampaignPrizeRepository.totalTickets(campaignId);
		int availableTickets = adminCampaignPrizeRepository.availableTickets(campaignId);
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_TICKETS_GENERATED",
				"Campaign",
				String.valueOf(campaignId),
				"{\"generatedCount\":" + generatedCount + ",\"totalTickets\":" + totalTickets + "}");
		return new AdminTicketGenerationResponse(
				campaignId,
				generatedCount,
				totalTickets,
				availableTickets,
				adminCampaignPrizeRepository.nonLastPrizeCount(campaignId));
	}

	private void validateRequest(AdminCampaignPrizeRequest request, int generatedTickets, int drawnTickets) {
		if (!request.lastPrize() && request.originalQuantity() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PRIZE_QUANTITY_REQUIRED", "一般獎項數量必須大於 0。");
		}
		if (request.originalQuantity() < generatedTickets) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PRIZE_QUANTITY_BELOW_TICKETS", "獎項數量不可小於已產生 ticket 數。");
		}
		if (request.originalQuantity() < drawnTickets) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PRIZE_QUANTITY_BELOW_DRAWN", "獎項數量不可小於已抽出數量。");
		}
	}

	private AdminCampaignPrizeRepository.CampaignRow requireCampaign(long campaignId) {
		AdminCampaignPrizeRepository.CampaignRow campaign = adminCampaignPrizeRepository.findCampaign(campaignId);
		if (campaign == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
		}
		return campaign;
	}

	private static String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private void rejectPrizeChangeWhenLocked(
			SessionPrincipal admin,
			AdminCampaignPrizeRepository.CampaignRow campaign,
			String action,
			String entityType,
			String entityId) {
		if (!PRIZE_LOCK_STATUSES.contains(campaign.status())) {
			return;
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				action,
				entityType,
				entityId,
				"{\"campaignId\":" + campaign.id() + ",\"status\":\"" + campaign.status() + "\"}");
		throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"CAMPAIGN_PRIZES_LOCKED",
				"已開抽、暫停或完抽的賞池不可直接修改獎項或 ticket，請建立修正版本並保留稽核紀錄。",
				Map.of("campaignId", campaign.id(), "status", campaign.status()));
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}
}
