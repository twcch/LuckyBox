package com.luckybox.wallet;

public record WalletLedgerResponse(
		long id,
		String type,
		int amount,
		String pointKind,
		int balanceAfter,
		String referenceType,
		Long referenceId,
		String reason,
		String createdAt) {
}
