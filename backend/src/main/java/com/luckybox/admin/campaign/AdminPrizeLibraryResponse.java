package com.luckybox.admin.campaign;

public record AdminPrizeLibraryResponse(
		long id,
		long campaignId,
		String campaignSlug,
		String campaignTitle,
		String campaignStatus,
		String campaignStatusLabel,
		String rank,
		String name,
		String description,
		String imageUrl,
		int originalQuantity,
		int remainingQuantity,
		int generatedTickets,
		int availableTickets,
		int drawnTickets,
		int sortOrder,
		boolean lastPrize,
		String createdAt,
		String updatedAt) {
}
