package com.luckybox.accountorder;

public record AccountPaymentOrderResponse(
		long id,
		String merchantTradeNo,
		int amount,
		int pointAmount,
		int bonusPointAmount,
		String status,
		String statusLabel,
		String createdAt,
		String paidAt) {
}
