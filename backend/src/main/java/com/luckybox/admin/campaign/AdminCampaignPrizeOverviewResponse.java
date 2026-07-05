package com.luckybox.admin.campaign;

import java.util.List;

public record AdminCampaignPrizeOverviewResponse(
		long campaignId,
		int totalPrizeQuantity,
		int remainingPrizeQuantity,
		int generatedTickets,
		int availableTickets,
		List<AdminCampaignPrizeResponse> prizes) {
}
