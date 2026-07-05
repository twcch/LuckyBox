package com.luckybox.admin.draworder;

import java.util.List;

public record AdminDrawOrderDetailResponse(
		long id,
		long userId,
		String userDisplayName,
		String maskedUserEmail,
		String campaignSlug,
		String campaignTitle,
		int quantity,
		int originalPointSpent,
		int discountAmount,
		int pointSpent,
		String couponCode,
		String status,
		String statusLabel,
		int resultCount,
		String prizeSummary,
		String idempotencyKey,
		String createdAt,
		String completedAt,
		List<AdminDrawOrderResultResponse> results,
		List<AdminDrawOrderLedgerResponse> ledgerRows) {
}
