package com.luckybox.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
		@NotBlank(message = "請輸入收件人")
		@Size(max = 80, message = "收件人過長")
		String recipientName,

		@NotBlank(message = "請輸入手機")
		@Size(max = 30, message = "手機過長")
		String phone,

		@Size(max = 10, message = "郵遞區號過長")
		String postalCode,

		@NotBlank(message = "請輸入縣市")
		@Size(max = 40, message = "縣市過長")
		String city,

		@NotBlank(message = "請輸入行政區")
		@Size(max = 40, message = "行政區過長")
		String district,

		@NotBlank(message = "請輸入地址")
		@Size(max = 180, message = "地址過長")
		String addressLine,

		boolean defaultAddress) {
}
