package com.luckybox.admin.user;

public record AdminUserResponse(
		long id,
		String maskedEmail,
		String displayName,
		String maskedPhone,
		String role,
		String roleLabel,
		String status,
		String statusLabel,
		String vipLevel,
		int cashPointBalance,
		int bonusPointBalance,
		int drawOrderCount,
		int prizeCount,
		int shipmentCount,
		String createdAt,
		String lastLoginAt) {
}
