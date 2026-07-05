package com.luckybox.wallet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record PaymentMockCompleteRequest(
		@NotNull(message = "付款訂單 id 必填")
		@Positive(message = "付款訂單 id 必須大於 0")
		Long orderId) {
}
