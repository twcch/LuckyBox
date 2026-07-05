package com.luckybox.admin.campaign;

public record AdminCampaignTicketResponse(
		long id,
		long campaignId,
		long prizeId,
		String serialNumber,
		String status,
		String statusLabel,
		String prizeRank,
		String prizeName,
		boolean lastPrize,
		Long drawId,
		Long drawnByUserId,
		String drawnByDisplayName,
		String drawnByEmail,
		String drawnAt,
		String createdAt,
		String updatedAt) {
}
