package com.luckybox.admin.draworder;

public record AdminDrawOrderResponse(
		long id,
		long userId,
		String userDisplayName,
		String maskedUserEmail,
		String campaignSlug,
		String campaignTitle,
		int quantity,
		int pointSpent,
		String status,
		String statusLabel,
		int resultCount,
		String prizeSummary,
		String createdAt,
		String completedAt) {
}
