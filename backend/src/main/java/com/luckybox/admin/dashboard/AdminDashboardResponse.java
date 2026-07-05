package com.luckybox.admin.dashboard;

import java.util.List;

public record AdminDashboardResponse(
		List<AdminDashboardMetricResponse> metrics,
		List<AdminDashboardMetricResponse> productMetrics,
		List<AdminDashboardShipmentResponse> requestedShipments,
		List<AdminDashboardActivityResponse> recentActivities) {
}
