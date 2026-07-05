package com.luckybox.campaign;

record CampaignQuery(
		String keyword,
		String sourceType,
		String status,
		String sort,
		int page,
		int size) {
}
