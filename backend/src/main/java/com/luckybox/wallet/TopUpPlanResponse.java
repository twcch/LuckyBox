package com.luckybox.wallet;

public record TopUpPlanResponse(
		String id,
		String label,
		int amount,
		int pointAmount,
		int bonusPointAmount) {
}
