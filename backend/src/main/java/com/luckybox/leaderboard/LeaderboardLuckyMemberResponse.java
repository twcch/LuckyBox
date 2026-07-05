package com.luckybox.leaderboard;

public record LeaderboardLuckyMemberResponse(
		int position,
		String displayName,
		int luckyWins,
		int topRankWins,
		int lastPrizeWins) {
}
