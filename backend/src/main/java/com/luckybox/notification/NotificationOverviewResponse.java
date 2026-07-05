package com.luckybox.notification;

import java.util.List;

public record NotificationOverviewResponse(
		int unreadCount,
		List<NotificationResponse> items) {
}
