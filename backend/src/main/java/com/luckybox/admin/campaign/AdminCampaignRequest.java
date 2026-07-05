package com.luckybox.admin.campaign;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record AdminCampaignRequest(
		@NotBlank String slug,
		@NotBlank String title,
		String subtitle,
		@NotBlank String description,
		String coverImageUrl,
		String bannerImageUrl,
		@NotBlank String sourceType,
		Boolean commercialUseConfirmed,
		Boolean officialLicenseConfirmed,
		String rightsNotice,
		Boolean ageRestricted,
		Integer minimumAge,
		String ageVerificationNote,
		String ipName,
		String brandName,
		@Min(1) int pricePerDraw,
		@Min(0) int totalTickets,
		@NotBlank String status,
		String salesStartAt,
		String salesEndAt,
		@NotBlank String shippingNote,
		@NotBlank String returnPolicyNote,
		@NotNull Boolean hasLastPrize,
		String lastPrizeRule,
		@NotBlank String fairnessMode,
		String seedHash) {
}
