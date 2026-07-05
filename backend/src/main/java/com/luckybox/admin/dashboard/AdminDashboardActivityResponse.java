package com.luckybox.admin.dashboard;

public record AdminDashboardActivityResponse(
		long id,
		String actorRole,
		String action,
		String entityType,
		String entityId,
		String createdAt) {
}
