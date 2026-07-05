package com.luckybox.wallet;

public record PaymentWebhookResponse(
		String provider,
		String eventId,
		String merchantTradeNo,
		String status,
		boolean processed,
		boolean duplicate,
		String orderStatus,
		String message) {
}
