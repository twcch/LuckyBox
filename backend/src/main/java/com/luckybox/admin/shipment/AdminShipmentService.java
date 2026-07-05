package com.luckybox.admin.shipment;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.notification.NotificationService;

@Service
class AdminShipmentService {

	private static final Set<String> FILTER_STATUSES = Set.of("REQUESTED", "SHIPPED", "DELIVERED", "RETURNED", "EXCHANGED");
	private static final Set<String> UPDATE_STATUSES = Set.of("REQUESTED", "SHIPPED", "DELIVERED");

	private final AdminShipmentRepository adminShipmentRepository;
	private final AuditLogHelper auditLogHelper;
	private final NotificationService notificationService;

	AdminShipmentService(
			AdminShipmentRepository adminShipmentRepository,
			AuditLogHelper auditLogHelper,
			NotificationService notificationService) {
		this.adminShipmentRepository = adminShipmentRepository;
		this.auditLogHelper = auditLogHelper;
		this.notificationService = notificationService;
	}

	List<AdminShipmentResponse> shipments(String status) {
		requireAdmin();
		return adminShipmentRepository.findShipments(
				normalizeStatus(status, false, FILTER_STATUSES, "物流狀態不正確。"));
	}

	@Transactional
	AdminShipmentResponse updateShipment(long shipmentId, UpdateShipmentRequest request) {
		SessionPrincipal admin = requireAdmin();
		String status = normalizeStatus(request.status(), true, UPDATE_STATUSES, "物流狀態只能更新為待處理、已出貨或已送達。");
		AdminShipmentResponse current = adminShipmentRepository.findShipment(shipmentId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "找不到指定出貨單。");
		}
		if (("SHIPPED".equals(status) || "DELIVERED".equals(status))
				&& (request.carrier() == null || request.carrier().isBlank()
				|| request.trackingNumber() == null || request.trackingNumber().isBlank())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKING_REQUIRED", "出貨或送達狀態必須填寫物流商與追蹤碼。");
		}
		if (!adminShipmentRepository.updateShipment(
				shipmentId,
				status,
				request.carrier(),
				request.trackingNumber(),
				request.adminNote())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "找不到指定出貨單。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_SHIPMENT_UPDATED",
				"Shipment",
				String.valueOf(shipmentId),
				"{\"status\":\"" + status + "\"}");
		AdminShipmentResponse updated = adminShipmentRepository.findShipment(shipmentId);
		notificationService.notifyShipmentUpdated(updated, current.status());
		return updated;
	}

	@Transactional
	AdminShipmentResponse resolveShipment(long shipmentId, ResolveShipmentRequest request) {
		SessionPrincipal admin = requireAdmin();
		String resolution = request == null || request.resolution() == null
				? "" : request.resolution().trim().toUpperCase();
		if (!"RETURNED".equals(resolution) && !"EXCHANGED".equals(resolution)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT_RESOLUTION", "處理方式只能是退回或換貨。");
		}
		String reason = request == null || request.reason() == null ? "" : request.reason().strip();
		if (reason.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SHIPMENT_RESOLUTION_REASON_REQUIRED", "請填寫處理原因以利稽核。");
		}
		if (reason.length() > 200) {
			reason = reason.substring(0, 200);
		}
		AdminShipmentResponse current = adminShipmentRepository.findShipment(shipmentId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "找不到指定出貨單。");
		}
		if (!"SHIPPED".equals(current.status()) && !"DELIVERED".equals(current.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SHIPMENT_NOT_RESOLVABLE",
					"只有已出貨或已送達的出貨單可做退回/換貨處理。");
		}
			// 退回：戰利品回到 IN_BOX 並解除出貨連結，可重新申請出貨；換貨：標記 EXCHANGED（由客服離線處理）。
		boolean clearLink = "RETURNED".equals(resolution);
		String prizeStatus = clearLink ? "IN_BOX" : "EXCHANGED";
		String noteReason = ("RETURNED".equals(resolution) ? "退回：" : "換貨：") + reason;
		if (!adminShipmentRepository.resolveShipment(shipmentId, resolution, prizeStatus, clearLink, noteReason)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "找不到指定出貨單。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_SHIPMENT_RESOLVED",
				"Shipment",
				String.valueOf(shipmentId),
				"{\"resolution\":\"" + resolution + "\",\"from\":\"" + current.status() + "\"}");
		AdminShipmentResponse updated = adminShipmentRepository.findShipment(shipmentId);
		notificationService.notifyShipmentUpdated(updated, current.status());
		return updated;
	}

	private static String normalizeStatus(String status, boolean required, Set<String> allowedStatuses, String message) {
		if (status == null || status.isBlank()) {
			if (required) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT_STATUS", message);
			}
			return null;
		}
		String normalized = status.trim().toUpperCase();
		if (!allowedStatuses.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT_STATUS", message);
		}
		return normalized;
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}
}
