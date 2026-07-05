package com.luckybox.coupon;

public record CouponSummary(
		long id,
		String code,
		String type,
		String typeLabel,
		String vipTier,
		String vipTierLabel,
		int value,
		int minSpend,
		Integer usageLimit,
		int usedCount,
		String startsAt,
		String endsAt) {
}
