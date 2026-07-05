package com.luckybox.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorCodeRequest(
		@NotBlank(message = "請輸入二階段驗證碼")
		@Pattern(regexp = "\\d{6}", message = "二階段驗證碼需為 6 位數字")
		String code) {
}
