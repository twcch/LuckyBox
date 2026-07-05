package com.luckybox.admin.campaign;

public record AdminCampaignPrizeResponse(
		long id,
		long campaignId,
		String rank,
		String name,
		String description,
		String imageUrl,
		int originalQuantity,
		int remainingQuantity,
		int generatedTickets,
		int availableTickets,
		int sortOrder,
		boolean lastPrize,
		String createdAt,
		String updatedAt) {
}
