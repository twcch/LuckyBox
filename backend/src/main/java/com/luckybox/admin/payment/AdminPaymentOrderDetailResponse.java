package com.luckybox.admin.payment;

import java.util.List;

public record AdminPaymentOrderDetailResponse(
		AdminPaymentOrderResponse order,
		String providerPayload,
		List<AdminPaymentWebhookEventResponse> webhookEvents) {
}
