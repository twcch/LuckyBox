package com.luckybox.wallet;

/**
 * Spend-threshold promo state for the wallet overview: the threshold, the bonus on offer, the user's
 * accumulated draw spend, how much more is needed, and whether it is active / already reached.
 */
public record SpendThresholdPromoResponse(
		boolean active,
		int threshold,
		int bonusPoints,
		int totalSpend,
		int remaining,
		boolean reached) {
}
