package com.luckybox.admin.walletledger;

public record AdminWalletLedgerResponse(
		long id,
		long userId,
		String userDisplayName,
		String maskedUserEmail,
		String type,
		String typeLabel,
		int amount,
		String pointKind,
		String pointKindLabel,
		int balanceAfter,
		String referenceType,
		Long referenceId,
		String reason,
		Long createdByUserId,
		String createdByDisplayName,
		String createdAt) {
}
