package com.luckybox.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "請輸入 Email")
		@Email(message = "Email 格式不正確")
		String email,

		@NotBlank(message = "請輸入密碼")
		String password,

		// 選填：僅已啟用二階段驗證的帳號需要提供 6 碼 TOTP。
		String totpCode) {
}
