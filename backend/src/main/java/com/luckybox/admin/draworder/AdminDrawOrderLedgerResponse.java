package com.luckybox.admin.draworder;

public record AdminDrawOrderLedgerResponse(
		long id,
		String type,
		String typeLabel,
		int amount,
		String pointKind,
		String pointKindLabel,
		int balanceAfter,
		String reason,
		Long createdBy,
		String createdAt) {
}
