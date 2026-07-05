package com.luckybox.leaderboard;

import java.util.List;

public record LeaderboardResponse(
		List<LeaderboardLiveDrawResponse> liveDraws,
		List<LeaderboardPopularCampaignResponse> popularCampaigns,
		List<LeaderboardLuckyMemberResponse> luckyMembers,
		String generatedAt) {
}
