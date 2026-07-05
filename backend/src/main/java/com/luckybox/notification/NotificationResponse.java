package com.luckybox.notification;

public record NotificationResponse(
		long id,
		String type,
		String title,
		String body,
		String linkUrl,
		String referenceType,
		Long referenceId,
		String readAt,
		String createdAt) {
}
