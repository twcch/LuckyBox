package com.luckybox.prizebox;

import java.util.List;
import java.util.Map;

public record PrizeBoxOverviewResponse(
		List<PrizeBoxItemResponse> items,
		List<PrizeBoxCampaignOption> campaigns,
		Map<String, Integer> statusCounts,
		String status,
		String campaignSlug) {
}
