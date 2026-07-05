package com.luckybox.checkin;

public record CheckInStatusResponse(
		boolean checkedInToday,
		int rewardAmount,
		int baseRewardAmount,
		int streakBonusAmount,
		int currentStreak,
		int totalCheckIns,
		String today,
		Integer nextStreakBonusAt,
		int nextStreakBonusAmount,
		int daysUntilNextStreakBonus) {
}
