package com.luckybox.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
		@NotBlank(message = "請輸入顯示名稱")
		@Size(max = 80, message = "顯示名稱過長")
		String displayName,

		@Size(max = 30, message = "手機過長")
		String phone) {
}
