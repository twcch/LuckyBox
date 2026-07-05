package com.luckybox.wallet;

interface LinePayClient {

	LinePayPaymentResult requestPayment(LinePayPaymentRequest request);

	LinePayConfirmResult confirmPayment(String transactionId, int amount, String currency);
}

record LinePayPaymentRequest(
		String orderId,
		int amount,
		String currency,
		String productName,
		String confirmUrl,
		String cancelUrl) {
}

record LinePayPaymentResult(
		String returnCode,
		String returnMessage,
		String transactionId,
		String webPaymentUrl,
		String appPaymentUrl,
		String rawPayload) {
}

record LinePayConfirmResult(
		String returnCode,
		String returnMessage,
		String rawPayload) {
}
