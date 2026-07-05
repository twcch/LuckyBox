package com.luckybox.account;

public record AuthUser(
		long id,
		String email,
		String displayName,
		String phone,
		String role,
		String status,
		String vipLevel,
		int cashPointBalance,
		int bonusPointBalance) {
}
