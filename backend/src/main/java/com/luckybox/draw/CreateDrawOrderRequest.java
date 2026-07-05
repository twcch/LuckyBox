package com.luckybox.draw;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateDrawOrderRequest(
		@NotBlank(message = "請指定賞池")
		String campaignSlug,

		@Min(value = 1, message = "至少需要抽 1 次")
		@Max(value = 10, message = "單次最多 10 抽")
		int quantity,

		@NotBlank(message = "請提供請求識別碼")
		String idempotencyKey,

		String couponCode) {

	public CreateDrawOrderRequest(String campaignSlug, int quantity, String idempotencyKey) {
		this(campaignSlug, quantity, idempotencyKey, null);
	}
}
