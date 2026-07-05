package com.luckybox.prizebox;

import java.util.List;

public record ShipmentResponse(
		long id,
		String status,
		int itemCount,
		int shippingFee,
		String recipientName,
		String phone,
		String postalCode,
		String city,
		String district,
		String addressLine,
		String carrier,
		String trackingNumber,
		String requestedAt,
		String shippedAt,
		String deliveredAt,
		List<PrizeBoxItemResponse> items) {
}
