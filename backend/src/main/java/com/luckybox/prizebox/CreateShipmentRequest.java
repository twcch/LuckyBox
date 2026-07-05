package com.luckybox.prizebox;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record CreateShipmentRequest(
		@NotEmpty(message = "請選擇要出貨的戰利品")
		List<@Positive(message = "戰利品 ID 不正確") Long> prizeIds,

		@Positive(message = "請選擇收件地址")
		long addressId,

		@Positive(message = "優惠券 ID 不正確")
		Long couponId) {
}
