package com.luckybox.wish;

import jakarta.validation.constraints.Size;

public record ModerateWishRequest(
		@Size(max = 40, message = "願望狀態不可超過 40 字")
		String status,

		@Size(max = 200, message = "審核備註不可超過 200 字")
		String note) {
}
