package com.luckybox.coupon;

public record CouponRedemptionResponse(
		long couponId,
		String code,
		int pointAmount,
		int bonusPointBalance,
		int totalAvailableBalance,
		String usedAt) {
}
