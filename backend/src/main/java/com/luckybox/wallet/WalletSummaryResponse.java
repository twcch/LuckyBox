package com.luckybox.wallet;

public record WalletSummaryResponse(
		int cashPointBalance,
		int bonusPointBalance,
		int lockedBalance,
		int totalAvailableBalance,
		int bonusPointExpiryDays,
		String bonusPointExpiryLabel) {

	public WalletSummaryResponse(
			int cashPointBalance,
			int bonusPointBalance,
			int lockedBalance,
			int totalAvailableBalance) {
		this(cashPointBalance, bonusPointBalance, lockedBalance, totalAvailableBalance, 0, expiryLabel(0));
	}

	WalletSummaryResponse withBonusPointExpiry(int expiryDays) {
		int normalizedDays = Math.max(0, expiryDays);
		return new WalletSummaryResponse(
				cashPointBalance,
				bonusPointBalance,
				lockedBalance,
				totalAvailableBalance,
				normalizedDays,
				expiryLabel(normalizedDays));
	}

	private static String expiryLabel(int expiryDays) {
		if (expiryDays <= 0) {
			return "紅利點目前未設定到期日。";
		}
		return "紅利點自入帳日起 " + expiryDays + " 天有效。";
	}
}
