package com.luckybox.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "請輸入 Email")
		@Email(message = "Email 格式不正確")
		@Size(max = 180, message = "Email 過長")
		String email,

		@NotBlank(message = "請輸入密碼")
		@Size(min = 8, max = 80, message = "密碼需為 8 到 80 字")
		String password,

		@NotBlank(message = "請輸入顯示名稱")
		@Size(max = 80, message = "顯示名稱過長")
		String displayName,

		@Size(max = 30, message = "手機過長")
		String phone,

		@Size(min = 12, max = 80, message = "visitorId 長度不正確")
		@Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "visitorId 格式不正確")
		String visitorId) {
}
