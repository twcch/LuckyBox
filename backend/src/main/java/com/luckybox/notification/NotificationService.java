package com.luckybox.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.admin.shipment.AdminShipmentResponse;
import com.luckybox.common.ApiException;
import com.luckybox.mail.EmailService;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final EmailService emailService;
	private final String appBaseUrl;

	NotificationService(
			NotificationRepository notificationRepository,
			EmailService emailService,
			@Value("${luckybox.app.base-url:http://localhost:5173}") String appBaseUrl) {
		this.notificationRepository = notificationRepository;
		this.emailService = emailService;
		this.appBaseUrl = normalizeBaseUrl(appBaseUrl);
	}

	public NotificationOverviewResponse notifications() {
		return notificationRepository.overview(currentUserId());
	}

	public NotificationResponse markRead(long notificationId) {
		NotificationResponse notification = notificationRepository.markRead(currentUserId(), notificationId);
		if (notification == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "找不到指定通知。");
		}
		return notification;
	}

	public void notifyCompensation(long userId, int amount) {
		notificationRepository.createNotification(
				userId,
				"COMPENSATION_GRANTED",
				"已發放補償點",
				"客服已為您發放 " + amount + " 點補償紅利，已存入您的點數錢包。",
				"/account/wallet",
				"Compensation",
				null);
	}

	public void notifyShipmentUpdated(AdminShipmentResponse shipment, String previousStatus) {
		if (shipment == null || shipment.status() == null || shipment.status().equals(previousStatus)) {
			return;
		}
		ShipmentNotification notification = shipmentNotification(shipment);
		if (notification == null) {
			return;
		}
		boolean created = notificationRepository.createShipmentNotification(
				shipment.userId(),
				notification.type(),
				notification.title(),
				notification.body(),
				notification.linkUrl(),
				shipment.id());
		if (created) {
			sendShipmentEmail(shipment.userId(), notification);
		}
	}

	private ShipmentNotification shipmentNotification(AdminShipmentResponse shipment) {
		return switch (shipment.status()) {
			case "SHIPPED" -> new ShipmentNotification(
					"SHIPMENT_SHIPPED",
					"出貨已交寄",
					"出貨單 #" + shipment.id() + " 已由 " + shipment.carrier()
							+ " 交寄，追蹤碼 " + shipment.trackingNumber() + "。",
					"/account/prizes");
			case "DELIVERED" -> new ShipmentNotification(
					"SHIPMENT_DELIVERED",
					"出貨已送達",
					"出貨單 #" + shipment.id() + " 已標記為送達，請到戰利品盒確認收貨狀態。",
					"/account/prizes");
			case "RETURNED" -> new ShipmentNotification(
					"SHIPMENT_RETURNED",
					"出貨已退回",
					"出貨單 #" + shipment.id() + " 已退回，相關戰利品已退回戰利品盒，可重新申請出貨。",
					"/account/prizes");
			case "EXCHANGED" -> new ShipmentNotification(
					"SHIPMENT_EXCHANGED",
					"商品換貨處理中",
						"出貨單 #" + shipment.id() + " 的商品已受理換貨，客服將與您聯繫接續換貨事宜。",
					"/account/prizes");
			default -> null;
		};
	}

	private void sendShipmentEmail(long userId, ShipmentNotification notification) {
		notificationRepository.findUserEmail(userId).ifPresent(email -> emailService.send(
				email,
				"LuckyBox：" + notification.title(),
				"""
						您好，

						%s

						您可以登入 LuckyBox 查看戰利品盒與出貨紀錄：
						%s

						LuckyBox 團隊""".formatted(notification.body(), absoluteUrl(notification.linkUrl()))));
	}

	private String absoluteUrl(String linkUrl) {
		if (linkUrl == null || linkUrl.isBlank()) {
			return appBaseUrl;
		}
		if (linkUrl.startsWith("http://") || linkUrl.startsWith("https://")) {
			return linkUrl;
		}
		if (linkUrl.startsWith("/")) {
			return appBaseUrl + linkUrl;
		}
		return appBaseUrl + "/" + linkUrl;
	}

	private static String normalizeBaseUrl(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return "http://localhost:5173";
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}

	private record ShipmentNotification(String type, String title, String body, String linkUrl) {
	}
}
