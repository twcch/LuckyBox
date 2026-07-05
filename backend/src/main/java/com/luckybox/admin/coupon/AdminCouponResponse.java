package com.luckybox.admin.coupon;

public record AdminCouponResponse(
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
		String endsAt,
		String status,
		String statusLabel,
		String createdAt,
		String updatedAt) {
}
