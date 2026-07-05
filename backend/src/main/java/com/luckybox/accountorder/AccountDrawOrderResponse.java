package com.luckybox.accountorder;

import java.util.List;

public record AccountDrawOrderResponse(
		long id,
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
		String createdAt,
		String completedAt,
		List<AccountDrawOrderResultResponse> results) {
}
