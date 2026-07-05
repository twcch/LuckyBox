package com.luckybox.campaign;

import java.util.List;

public record CampaignDetail(
		long id,
		String slug,
		String title,
		String subtitle,
		String description,
		String coverImageUrl,
		String bannerImageUrl,
		String sourceType,
		String sourceTypeLabel,
		String status,
		String statusLabel,
		int pricePerDraw,
		int totalTickets,
		int remainingTickets,
		boolean hasLastPrize,
		String lastPrizeRule,
		String shippingNote,
		String returnPolicyNote,
		String fairnessMode,
		String seedHash,
		double remainingRate,
		String rightsNotice,
		boolean ageRestricted,
		Integer minimumAge,
		String ageRestrictionLabel,
		String ageVerificationNote,
		List<PrizeSummary> prizes) {
}
