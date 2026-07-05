package com.luckybox.wallet;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentOrderRequest(
		@NotBlank(message = "請選擇儲值方案")
		String planId,
		String provider) {
}
