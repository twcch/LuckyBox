package com.luckybox.admin.auditsummary;

import java.util.List;

public record AuditSummaryResponse(
		String slug,
		String title,
		String status,
		String fairnessMode,
		int totalTickets,
		int remainingTickets,
		int drawnTickets,
		int totalDrawResults,
		int uniqueDrawers,
		int totalOrders,
		String firstDrawAt,
		String lastDrawAt,
		boolean hasLastPrize,
		boolean lastPrizeAwarded,
		String seedHash,
		boolean revealed,
		String revealedSeed,
		List<PrizeDistributionResponse> prizeDistribution) {
}
