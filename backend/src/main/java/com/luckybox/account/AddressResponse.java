package com.luckybox.account;

public record AddressResponse(
		long id,
		String recipientName,
		String phone,
		String postalCode,
		String city,
		String district,
		String addressLine,
		boolean defaultAddress) {
}
