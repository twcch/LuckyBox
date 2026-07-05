package com.luckybox.admin.shipment;

public record AdminShipmentItemResponse(
		long id,
		String campaignTitle,
		String prizeRank,
		String prizeName,
		String ticketSerialNumber,
		String status) {
}
