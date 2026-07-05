package com.luckybox.admin.campaign;

public record AdminTicketGenerationResponse(
		long campaignId,
		int generatedCount,
		int totalTickets,
		int availableTickets,
		int prizeCount) {
}
