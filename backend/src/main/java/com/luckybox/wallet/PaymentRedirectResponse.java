package com.luckybox.wallet;

import java.util.Map;

public record PaymentRedirectResponse(
		long orderId,
		String provider,
		String merchantTradeNo,
		String redirectUrl,
		String appRedirectUrl,
		String qrImageUrl,
		String transactionId,
		Map<String, String> metadata) {
}
