package com.luckybox.admin.auditsummary;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.common.ApiException;

@Service
class AuditSummaryService {

	private final AuditSummaryRepository auditSummaryRepository;

	AuditSummaryService(AuditSummaryRepository auditSummaryRepository) {
		this.auditSummaryRepository = auditSummaryRepository;
	}

	AuditSummaryResponse summary(String slug) {
		requireAdmin();
		AuditSummaryRepository.CampaignBasics campaign = auditSummaryRepository.findCampaign(slug);
		if (campaign == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
		}
		List<PrizeDistributionResponse> prizeDistribution = auditSummaryRepository.findPrizeDistribution(campaign.id());
		AuditSummaryRepository.DrawStats stats = auditSummaryRepository.findDrawStats(campaign.id());
		boolean lastPrizeAwarded = prizeDistribution.stream()
				.anyMatch(prize -> prize.lastPrize() && prize.drawnCount() > 0);
		int drawnTickets = campaign.totalTickets() - campaign.remainingTickets();
		return new AuditSummaryResponse(
				campaign.slug(),
				campaign.title(),
				campaign.status(),
				campaign.fairnessMode(),
				campaign.totalTickets(),
				campaign.remainingTickets(),
				drawnTickets,
				stats.totalDraws(),
				stats.uniqueDrawers(),
				stats.totalOrders(),
				stats.firstDrawAt(),
				stats.lastDrawAt(),
				campaign.hasLastPrize(),
				lastPrizeAwarded,
				campaign.seedHash(),
				campaign.revealedSeed() != null,
				campaign.revealedSeed(),
				prizeDistribution);
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}
}
