package com.luckybox.campaign;

import java.util.List;

public record CampaignPage(
		List<CampaignSummary> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		String sort,
		String keyword,
		String sourceType,
		String status) {
}
