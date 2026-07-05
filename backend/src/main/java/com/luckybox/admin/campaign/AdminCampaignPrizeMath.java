package com.luckybox.admin.campaign;

import java.util.List;

final class AdminCampaignPrizeMath {

	private AdminCampaignPrizeMath() {
	}

	static int totalRegularQuantity(List<AdminCampaignPrizeResponse> prizes) {
		return prizes.stream()
				.filter(prize -> !prize.lastPrize())
				.mapToInt(AdminCampaignPrizeResponse::originalQuantity)
				.sum();
	}

	static int remainingRegularQuantity(List<AdminCampaignPrizeResponse> prizes) {
		return prizes.stream()
				.filter(prize -> !prize.lastPrize())
				.mapToInt(AdminCampaignPrizeResponse::remainingQuantity)
				.sum();
	}
}
