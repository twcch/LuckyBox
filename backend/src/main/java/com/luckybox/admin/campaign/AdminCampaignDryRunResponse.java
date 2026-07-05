package com.luckybox.admin.campaign;

import java.util.List;

public record AdminCampaignDryRunResponse(
		long campaignId,
		int requestedQuantity,
		int availableTickets,
		int totalTickets,
		List<Result> results) {

	public record Result(
			String serialNumber,
			String rank,
			String prizeName) {
	}
}
