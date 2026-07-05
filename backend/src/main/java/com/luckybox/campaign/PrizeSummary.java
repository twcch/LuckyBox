package com.luckybox.campaign;

public record PrizeSummary(
		long id,
		String rank,
		String name,
		String description,
		int originalQuantity,
		int remainingQuantity,
		boolean lastPrize,
		double probability) {
}
