package com.luckybox.admin.shipment;

import java.util.List;

public record AdminShipmentResponse(
		long id,
		long userId,
		String userEmail,
		String userDisplayName,
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
		String adminNote,
		String requestedAt,
		String shippedAt,
		String deliveredAt,
		List<AdminShipmentItemResponse> items) {
}
