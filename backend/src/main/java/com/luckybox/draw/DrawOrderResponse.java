package com.luckybox.draw;

import java.util.List;

public record DrawOrderResponse(
		long id,
		String campaignSlug,
		String campaignTitle,
		int quantity,
		int originalPointSpent,
		int discountAmount,
		int pointSpent,
		String couponCode,
		String status,
		int balanceAfter,
		String completedAt,
		List<DrawResultResponse> results) {
}
