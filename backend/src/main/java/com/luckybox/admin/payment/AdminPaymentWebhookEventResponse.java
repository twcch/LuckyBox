package com.luckybox.admin.payment;

public record AdminPaymentWebhookEventResponse(
		String provider,
		String eventId,
		String merchantTradeNo,
		String status,
		int amount,
		boolean processed,
		String message,
		String createdAt,
		String processedAt,
		String rawPayload) {
}
