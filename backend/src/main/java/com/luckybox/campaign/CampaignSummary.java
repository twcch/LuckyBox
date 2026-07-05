package com.luckybox.campaign;

public record CampaignSummary(
		long id,
		String slug,
		String title,
		String subtitle,
		String coverImageUrl,
		String sourceType,
		String sourceTypeLabel,
		String status,
		String statusLabel,
		int pricePerDraw,
		int totalTickets,
		int remainingTickets,
		boolean hasLastPrize,
		String rareHint,
		double remainingRate,
		boolean ageRestricted,
		Integer minimumAge,
		String ageRestrictionLabel) {
}
