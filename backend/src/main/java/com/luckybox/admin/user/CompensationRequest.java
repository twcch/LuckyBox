package com.luckybox.admin.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 客服補償發點輸入：amount 為正整數補償點，reason 必填以利稽核。 */
public record CompensationRequest(
		@NotNull(message = "請輸入補償點數")
		@Max(value = 1_000_000, message = "補償點數不可超過 1000000")
		Integer amount,

		@Size(max = 500, message = "補償原因不可超過 500 字")
		String reason) {
}
