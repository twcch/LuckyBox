package com.luckybox.leaderboard;

public record LeaderboardPopularCampaignResponse(
		long campaignId,
		String slug,
		String title,
		String status,
		String statusLabel,
		int pricePerDraw,
		int totalTickets,
		int remainingTickets,
		int soldTickets,
		double soldRate,
		int drawCount,
		int uniqueDrawers,
		String rareHint) {
}
