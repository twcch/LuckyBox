package com.luckybox.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank(message = "請提供重設碼")
		String token,

		@NotBlank(message = "請輸入新密碼")
		@Size(min = 8, max = 80, message = "密碼需為 8 到 80 字")
		String password) {
}
