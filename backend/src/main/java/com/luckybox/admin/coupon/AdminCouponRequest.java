package com.luckybox.admin.coupon;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminCouponRequest(
		@NotBlank(message = "優惠券代碼不可空白")
		@Size(max = 32, message = "優惠券代碼不可超過 32 字")
		String code,

		@NotBlank(message = "優惠券類型不可空白")
		@Size(max = 40, message = "優惠券類型不可超過 40 字")
		String type,

		@Size(max = 40, message = "VIP 等級不可超過 40 字")
		String vipTier,

		@Min(value = 0, message = "優惠券面額不可為負數")
		@Max(value = 1_000_000, message = "優惠券面額不可超過 1000000")
		int value,

		@Min(value = 0, message = "最低消費不可為負數")
		@Max(value = 1_000_000, message = "最低消費不可超過 1000000")
		int minSpend,

		@Positive(message = "使用上限必須大於 0")
		@Max(value = 1_000_000, message = "使用上限不可超過 1000000")
		Integer usageLimit,

		@Size(max = 80, message = "開始時間不可超過 80 字")
		String startsAt,

		@Size(max = 80, message = "結束時間不可超過 80 字")
		String endsAt,

		@NotBlank(message = "優惠券狀態不可空白")
		@Size(max = 40, message = "優惠券狀態不可超過 40 字")
		String status) {
}
