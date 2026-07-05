package com.luckybox.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.luckybox.admin.shipment.AdminShipmentResponse;
import com.luckybox.mail.EmailService;

class NotificationServiceTest {

	@Test
	void notifyShipmentUpdatedSendsEmailWithRealUserEmailWhenNotificationIsCreated() {
		NotificationRepository repository = Mockito.mock(NotificationRepository.class);
		EmailService emailService = Mockito.mock(EmailService.class);
		when(repository.createShipmentNotification(
				eq(42L),
				eq("SHIPMENT_SHIPPED"),
				eq("出貨已交寄"),
				anyString(),
				eq("/account/prizes"),
				eq(99L))).thenReturn(true);
		when(repository.findUserEmail(42L)).thenReturn(Optional.of("ship-user@example.com"));
		NotificationService service = new NotificationService(
				repository,
				emailService,
				"https://app.luckybox.test/");

		service.notifyShipmentUpdated(shipment("SHIPPED"), "REQUESTED");

		verify(repository).createShipmentNotification(
				eq(42L),
				eq("SHIPMENT_SHIPPED"),
				eq("出貨已交寄"),
				eq("出貨單 #99 已由 黑貓宅急便 交寄，追蹤碼 TA123456789。"),
				eq("/account/prizes"),
				eq(99L));
		verify(emailService).send(
				eq("ship-user@example.com"),
				eq("LuckyBox：出貨已交寄"),
				eq("""
						您好，

						出貨單 #99 已由 黑貓宅急便 交寄，追蹤碼 TA123456789。

						您可以登入 LuckyBox 查看戰利品盒與出貨紀錄：
						https://app.luckybox.test/account/prizes

						LuckyBox 團隊"""));
	}

	@Test
	void notifyShipmentUpdatedDoesNotEmailWhenStatusIsUnchanged() {
		NotificationRepository repository = Mockito.mock(NotificationRepository.class);
		EmailService emailService = Mockito.mock(EmailService.class);
		NotificationService service = new NotificationService(repository, emailService, "https://app.luckybox.test");

		service.notifyShipmentUpdated(shipment("SHIPPED"), "SHIPPED");

		verify(repository, never()).createShipmentNotification(
				eq(42L),
				anyString(),
				anyString(),
				anyString(),
				anyString(),
				eq(99L));
		verify(emailService, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void notifyShipmentUpdatedDoesNotEmailWhenNotificationAlreadyExists() {
		NotificationRepository repository = Mockito.mock(NotificationRepository.class);
		EmailService emailService = Mockito.mock(EmailService.class);
		when(repository.createShipmentNotification(
				eq(42L),
				eq("SHIPMENT_DELIVERED"),
				eq("出貨已送達"),
				anyString(),
				eq("/account/prizes"),
				eq(99L))).thenReturn(false);
		NotificationService service = new NotificationService(repository, emailService, "https://app.luckybox.test");

		service.notifyShipmentUpdated(shipment("DELIVERED"), "SHIPPED");

		verify(repository, never()).findUserEmail(42L);
		verify(emailService, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void notifyShipmentUpdatedKeepsInAppNotificationWhenUserEmailIsMissing() {
		NotificationRepository repository = Mockito.mock(NotificationRepository.class);
		EmailService emailService = Mockito.mock(EmailService.class);
		when(repository.createShipmentNotification(
				eq(42L),
				eq("SHIPMENT_RETURNED"),
				eq("出貨已退回"),
				anyString(),
				eq("/account/prizes"),
				eq(99L))).thenReturn(true);
		when(repository.findUserEmail(42L)).thenReturn(Optional.empty());
		NotificationService service = new NotificationService(repository, emailService, "https://app.luckybox.test");

		service.notifyShipmentUpdated(shipment("RETURNED"), "SHIPPED");

		verify(repository).createShipmentNotification(
				eq(42L),
				eq("SHIPMENT_RETURNED"),
				eq("出貨已退回"),
				anyString(),
				eq("/account/prizes"),
				eq(99L));
		verify(emailService, never()).send(anyString(), anyString(), anyString());
	}

	private static AdminShipmentResponse shipment(String status) {
		return new AdminShipmentResponse(
				99L,
				42L,
				"sh***@e***.com",
				"出貨測試玩家",
				status,
				1,
				80,
				"測試收件人",
				"0912345678",
				"100",
				"台北市",
				"中正區",
				"測試路 1 號",
				"黑貓宅急便",
				"TA123456789",
				"已交寄",
				"2026-07-05T09:00:00Z",
				"2026-07-05T10:00:00Z",
				null,
				List.of());
	}
}
