package com.luckybox.prizebox;

public record PrizeBoxItemResponse(
		long id,
		String campaignSlug,
		String campaignTitle,
		long prizeId,
		String prizeRank,
		String prizeName,
		String prizeDescription,
		String ticketSerialNumber,
		String status,
		Long shipmentId,
		String acquiredAt) {
}
