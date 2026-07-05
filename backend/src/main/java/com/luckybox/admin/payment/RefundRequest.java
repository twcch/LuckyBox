package com.luckybox.admin.payment;

import jakarta.validation.constraints.Size;

public record RefundRequest(
		@Size(max = 200, message = "退款原因不可超過 200 字")
		String reason) {
}
