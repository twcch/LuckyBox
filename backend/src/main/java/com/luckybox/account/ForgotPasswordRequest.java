package com.luckybox.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
		@NotBlank(message = "請輸入 Email")
		@Email(message = "Email 格式不正確")
		@Size(max = 180, message = "Email 過長")
		String email) {
}
