package com.luckybox.wallet;

interface JkoPayClient {

	JkoPayEntryResult createEntry(JkoPayEntryRequest request);
}

record JkoPayEntryRequest(
		String platformOrderId,
		String storeId,
		String currency,
		int totalPrice,
		int finalPrice,
		String confirmUrl,
		String resultUrl,
		String resultDisplayUrl,
		String productName) {
}

record JkoPayEntryResult(
		String result,
		String message,
		String paymentUrl,
		String qrImageUrl,
		String qrTimeout,
		String rawPayload) {
}
