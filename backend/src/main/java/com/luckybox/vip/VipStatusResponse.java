package com.luckybox.vip;

/** 會員 VIP 狀態：目前等級、累積抽賞消費、下一級門檻與升級進度。 */
public record VipStatusResponse(
		String tier,
		String tierLabel,
		int totalSpend,
		String nextTier,
		String nextTierLabel,
		int nextTierThreshold,
		int spendToNextTier,
		int progressPercent) {
}
