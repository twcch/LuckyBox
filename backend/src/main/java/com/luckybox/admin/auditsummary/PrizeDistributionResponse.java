package com.luckybox.admin.auditsummary;

public record PrizeDistributionResponse(
		String rank,
		String name,
		int originalQuantity,
		int drawnCount,
		int remainingQuantity,
		boolean lastPrize) {
}
