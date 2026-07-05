package com.luckybox.wallet;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentWebhookRequest(
		@NotBlank(message = "缺少 eventId")
		@Size(max = 120, message = "eventId 過長")
		String eventId,

		@NotBlank(message = "缺少 merchantTradeNo")
		@Size(max = 120, message = "merchantTradeNo 過長")
		String merchantTradeNo,

		@Min(value = 0, message = "金額不可為負數")
		int amount,

		@NotBlank(message = "缺少付款狀態")
		@Pattern(regexp = "PAID|FAILED|CANCELED", message = "付款狀態不正確")
		String status,

		@Size(max = 80, message = "occurredAt 過長")
		String occurredAt) {

	String signaturePayload() {
		return eventId + "|" + merchantTradeNo + "|" + amount + "|" + status;
	}
}
