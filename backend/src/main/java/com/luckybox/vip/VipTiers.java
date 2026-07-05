package com.luckybox.vip;

import java.util.Set;

public final class VipTiers {

	public static final Set<String> COUPON_REQUIRED_TIERS = Set.of("SILVER", "GOLD", "PLATINUM");

	private VipTiers() {
	}

	public static boolean isCouponRequiredTier(String tier) {
		return COUPON_REQUIRED_TIERS.contains(normalize(tier));
	}

	public static boolean isEligible(String currentTier, String requiredTier) {
		return rank(currentTier) >= rank(requiredTier);
	}

	public static int rank(String tier) {
		return switch (normalize(tier)) {
			case "PLATINUM" -> 3;
			case "GOLD" -> 2;
			case "SILVER" -> 1;
			default -> 0;
		};
	}

	public static String couponRequirementLabel(String tier) {
		return switch (normalize(tier)) {
			case "SILVER" -> "銀卡以上";
			case "GOLD" -> "金卡以上";
			case "PLATINUM" -> "白金卡";
			default -> null;
		};
	}

	public static String normalize(String tier) {
		return tier == null ? "" : tier.trim().toUpperCase();
	}
}
