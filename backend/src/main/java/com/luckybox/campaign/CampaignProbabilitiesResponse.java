package com.luckybox.campaign;

import java.util.List;

public record CampaignProbabilitiesResponse(
		String slug,
		int totalTickets,
		int remainingTickets,
		List<PrizeSummary> prizes) {
}
