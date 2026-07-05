package com.luckybox.wallet;

public record PaymentOrderResponse(
		long id,
		String provider,
		String merchantTradeNo,
		int amount,
		int pointAmount,
		int bonusPointAmount,
		String status,
		String createdAt,
		String paidAt) {
}
