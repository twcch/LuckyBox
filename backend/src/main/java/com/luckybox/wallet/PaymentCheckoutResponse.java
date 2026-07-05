package com.luckybox.wallet;

import java.util.Map;

public record PaymentCheckoutResponse(
		long orderId,
		String provider,
		String merchantTradeNo,
		String actionUrl,
		String method,
		Map<String, String> fields) {
}
