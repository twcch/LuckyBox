package com.luckybox.leaderboard;

public record LeaderboardLiveDrawResponse(
		long drawResultId,
		long drawOrderId,
		String maskedDisplayName,
		String campaignSlug,
		String campaignTitle,
		String prizeRank,
		String prizeName,
		int resultIndex,
		String createdAt) {
}
