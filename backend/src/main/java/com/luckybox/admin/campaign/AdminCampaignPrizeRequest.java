package com.luckybox.admin.campaign;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record AdminCampaignPrizeRequest(
		@NotBlank String rank,
		@NotBlank String name,
		String description,
		String imageUrl,
		@Min(0) int originalQuantity,
		@Min(1) int sortOrder,
		@NotNull Boolean lastPrize) {
}
