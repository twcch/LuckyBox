package com.luckybox.admin.dashboard;

public record AdminDashboardMetricResponse(
		String key,
		String label,
		String value,
		String helper,
		String tone) {
}
