package com.luckybox.admin.payment;

public record AdminPaymentOrderResponse(
		long id,
		long userId,
		String userDisplayName,
		String maskedUserEmail,
		String provider,
		String merchantTradeNo,
		int amount,
		int pointAmount,
		int bonusPointAmount,
		int totalPointAmount,
		String status,
		String statusLabel,
		String createdAt,
		String paidAt) {
}
