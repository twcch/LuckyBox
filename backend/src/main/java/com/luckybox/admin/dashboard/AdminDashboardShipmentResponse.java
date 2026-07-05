package com.luckybox.admin.dashboard;

public record AdminDashboardShipmentResponse(
		long id,
		String userDisplayName,
		String userEmail,
		int itemCount,
		String requestedAt) {
}
