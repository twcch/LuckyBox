package com.luckybox.leaderboard;

import java.util.List;

public record CampaignDrawHistoryResponse(
		List<LeaderboardLiveDrawResponse> draws,
		String generatedAt) {
}
