package com.luckybox.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/notifications")
class NotificationController {

	private final NotificationService notificationService;

	NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	NotificationOverviewResponse notifications() {
		return notificationService.notifications();
	}

	@PatchMapping("/{notificationId}/read")
	NotificationResponse markRead(@PathVariable long notificationId) {
		return notificationService.markRead(notificationId);
	}
}
