package com.luckybox.admin.approval;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.admin.payment.AdminPaymentOrderResponse;
import com.luckybox.admin.payment.AdminPaymentOrderService;
import com.luckybox.admin.payment.RefundRequest;
import com.luckybox.admin.user.AdminUserService;
import com.luckybox.admin.user.CompensationRequest;
import com.luckybox.admin.user.CompensationResponse;
import com.luckybox.admin.walletledger.AdminWalletLedgerResponse;
import com.luckybox.admin.walletledger.AdminWalletLedgerService;
import com.luckybox.admin.walletledger.WalletAdjustmentRequest;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
class AdminApprovalService {

	private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
	private static final Set<String> TYPES = Set.of("WALLET_ADJUSTMENT", "PAYMENT_REFUND", "COMPENSATION");
	private static final int REASON_MAX_LENGTH = 200;

	private final AdminApprovalRepository adminApprovalRepository;
	private final AdminWalletLedgerService adminWalletLedgerService;
	private final AdminPaymentOrderService adminPaymentOrderService;
	private final AdminUserService adminUserService;
	private final AuditLogHelper auditLogHelper;

	AdminApprovalService(
			AdminApprovalRepository adminApprovalRepository,
			AdminWalletLedgerService adminWalletLedgerService,
			AdminPaymentOrderService adminPaymentOrderService,
			AdminUserService adminUserService,
			AuditLogHelper auditLogHelper) {
		this.adminApprovalRepository = adminApprovalRepository;
		this.adminWalletLedgerService = adminWalletLedgerService;
		this.adminPaymentOrderService = adminPaymentOrderService;
		this.adminUserService = adminUserService;
		this.auditLogHelper = auditLogHelper;
	}

	List<AdminApprovalRequestResponse> requests(String status, String type) {
		requireAdmin();
		return adminApprovalRepository.findRequests(normalizeOptional(status, STATUSES, "INVALID_APPROVAL_STATUS"),
				normalizeOptional(type, TYPES, "INVALID_APPROVAL_TYPE"));
	}

	@Transactional
	AdminApprovalRequestResponse requestWalletAdjustment(WalletAdjustmentRequest request) {
		SessionPrincipal admin = requireAdmin();
		if (request == null || request.userId() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_PAYLOAD", "請指定要調整的會員。");
		}
		if (request.pointKind() == null || request.pointKind().isBlank() || request.amount() == null || request.amount() == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_PAYLOAD", "請填寫點數種類與非 0 調整點數。");
		}
		String reason = normalizeRequiredReason(request.reason(), "APPROVAL_REASON_REQUIRED", "請填寫審核原因。");
		long id = createRequest(
				admin,
				"WALLET_ADJUSTMENT",
				"User",
				String.valueOf(request.userId()),
				walletAdjustmentPayload(request, reason),
				reason);
		return adminApprovalRepository.findResponse(id);
	}

	@Transactional
	AdminApprovalRequestResponse requestPaymentRefund(long orderId, RefundRequest request) {
		SessionPrincipal admin = requireAdmin();
		if (orderId <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_PAYLOAD", "請指定要退款的訂單。");
		}
		String reason = normalizeRequiredReason(request == null ? null : request.reason(),
				"APPROVAL_REASON_REQUIRED", "請填寫退款審核原因。");
		long id = createRequest(
				admin,
				"PAYMENT_REFUND",
				"PaymentOrder",
				String.valueOf(orderId),
				paymentRefundPayload(orderId, reason),
				reason);
		return adminApprovalRepository.findResponse(id);
	}

	@Transactional
	AdminApprovalRequestResponse requestCompensation(long userId, CompensationRequest request) {
		SessionPrincipal admin = requireAdmin();
		if (userId <= 0 || request == null || request.amount() == null || request.amount() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_PAYLOAD", "請指定會員與正整數補償點數。");
		}
		String reason = normalizeRequiredReason(request == null ? null : request.reason(),
				"APPROVAL_REASON_REQUIRED", "請填寫補償審核原因。");
		long id = createRequest(
				admin,
				"COMPENSATION",
				"User",
				String.valueOf(userId),
				compensationPayload(userId, request == null ? null : request.amount(), reason),
				reason);
		return adminApprovalRepository.findResponse(id);
	}

	@Transactional
	AdminApprovalRequestResponse approve(long requestId) {
		SessionPrincipal admin = requireSuperAdmin();
		AdminApprovalRepository.ApprovalRequestRow row = pendingRow(requestId);
		ApprovalResult result = execute(row);
		if (!adminApprovalRepository.markApproved(requestId, admin.user().id(), result.entityType(), result.entityId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "APPROVAL_NOT_PENDING", "此審核單已處理。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_APPROVAL_APPROVED",
				"AdminApprovalRequest",
				String.valueOf(requestId),
				"{\"type\":\"" + row.type() + "\",\"resultEntityType\":\"" + result.entityType()
						+ "\",\"resultEntityId\":\"" + result.entityId() + "\"}");
		return adminApprovalRepository.findResponse(requestId);
	}

	@Transactional
	AdminApprovalRequestResponse reject(long requestId, AdminApprovalReviewRequest request) {
		SessionPrincipal admin = requireSuperAdmin();
		pendingRow(requestId);
		String reason = normalizeOptionalReason(request == null ? null : request.reason());
		if (!adminApprovalRepository.markRejected(requestId, admin.user().id(), reason)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "APPROVAL_NOT_PENDING", "此審核單已處理。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_APPROVAL_REJECTED",
				"AdminApprovalRequest",
				String.valueOf(requestId),
				reason == null ? "{}" : "{\"reason\":\"" + escapeJson(reason) + "\"}");
		return adminApprovalRepository.findResponse(requestId);
	}

	private long createRequest(
			SessionPrincipal admin,
			String type,
			String entityType,
			String entityId,
			String payloadJson,
			String reason) {
		long id = adminApprovalRepository.createRequest(
				type,
				entityType,
				entityId,
				payloadJson,
				reason,
				admin.user().id());
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_APPROVAL_REQUESTED",
				"AdminApprovalRequest",
				String.valueOf(id),
				"{\"type\":\"" + type + "\",\"entityType\":\"" + entityType + "\",\"entityId\":\"" + entityId + "\"}");
		return id;
	}

	private AdminApprovalRepository.ApprovalRequestRow pendingRow(long requestId) {
		AdminApprovalRepository.ApprovalRequestRow row = adminApprovalRepository.findRow(requestId);
		if (row == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "找不到指定審核單。");
		}
		if (!"PENDING".equals(row.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "APPROVAL_NOT_PENDING", "此審核單已處理。");
		}
		return row;
	}

	private ApprovalResult execute(AdminApprovalRepository.ApprovalRequestRow row) {
		return switch (row.type()) {
			case "WALLET_ADJUSTMENT" -> {
				WalletAdjustmentRequest payload = new WalletAdjustmentRequest(
						longValue(row.payloadJson(), "userId"),
						stringValue(row.payloadJson(), "pointKind"),
						intValue(row.payloadJson(), "amount"),
						stringValue(row.payloadJson(), "reason"));
				AdminWalletLedgerResponse ledger = adminWalletLedgerService.applyAdjustment(payload);
				yield new ApprovalResult("WalletLedger", String.valueOf(ledger.id()));
			}
			case "PAYMENT_REFUND" -> {
				Long orderId = longValue(row.payloadJson(), "orderId");
				AdminPaymentOrderResponse order = adminPaymentOrderService
						.refundPaymentOrder(orderId == null ? 0 : orderId, new RefundRequest(stringValue(row.payloadJson(), "reason")));
				yield new ApprovalResult("PaymentOrder", String.valueOf(order.id()));
			}
			case "COMPENSATION" -> {
				Long userId = longValue(row.payloadJson(), "userId");
				CompensationResponse compensation = adminUserService.grantCompensation(
						userId == null ? 0 : userId,
						new CompensationRequest(intValue(row.payloadJson(), "amount"), stringValue(row.payloadJson(), "reason")));
				yield new ApprovalResult("WalletLedger", String.valueOf(compensation.ledgerId()));
			}
			default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_TYPE", "審核類型不正確。");
		};
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private SessionPrincipal requireSuperAdmin() {
		return SecurityPrincipals.requireSuperAdmin();
	}

	private static String normalizeOptional(String value, Set<String> allowed, String code) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toUpperCase();
		if (!allowed.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, "審核篩選條件不正確。");
		}
		return normalized;
	}

	private static String normalizeRequiredReason(String value, String code, String message) {
		String normalized = normalizeOptionalReason(value);
		if (normalized == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		return normalized;
	}

	private static String normalizeOptionalReason(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		return normalized.length() > REASON_MAX_LENGTH ? normalized.substring(0, REASON_MAX_LENGTH) : normalized;
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String walletAdjustmentPayload(WalletAdjustmentRequest request, String reason) {
		return "{"
				+ "\"userId\":" + request.userId()
				+ ",\"pointKind\":\"" + escapeJson(request.pointKind()) + "\""
				+ ",\"amount\":" + request.amount()
				+ ",\"reason\":\"" + escapeJson(reason) + "\""
				+ "}";
	}

	private static String paymentRefundPayload(long orderId, String reason) {
		return "{"
				+ "\"orderId\":" + orderId
				+ ",\"reason\":\"" + escapeJson(reason) + "\""
				+ "}";
	}

	private static String compensationPayload(long userId, Integer amount, String reason) {
		return "{"
				+ "\"userId\":" + userId
				+ ",\"amount\":" + (amount == null ? "null" : amount)
				+ ",\"reason\":\"" + escapeJson(reason) + "\""
				+ "}";
	}

	private static Long longValue(String json, String key) {
		String value = rawValue(json, key);
		if (value == null || value.equals("null")) {
			return null;
		}
		return Long.parseLong(value);
	}

	private static Integer intValue(String json, String key) {
		String value = rawValue(json, key);
		if (value == null || value.equals("null")) {
			return null;
		}
		return Integer.parseInt(value);
	}

	private static String stringValue(String json, String key) {
		String marker = "\"" + key + "\":\"";
		int start = json.indexOf(marker);
		if (start < 0) {
			return null;
		}
		int cursor = start + marker.length();
		StringBuilder value = new StringBuilder();
		while (cursor < json.length()) {
			char current = json.charAt(cursor);
			if (current == '\\' && cursor + 1 < json.length()) {
				value.append(json.charAt(cursor + 1));
				cursor += 2;
				continue;
			}
			if (current == '"') {
				return value.toString();
			}
			value.append(current);
			cursor++;
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL_PAYLOAD", "審核內容格式不正確。");
	}

	private static String rawValue(String json, String key) {
		String marker = "\"" + key + "\":";
		int start = json.indexOf(marker);
		if (start < 0) {
			return null;
		}
		int cursor = start + marker.length();
		int end = cursor;
		while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
			end++;
		}
		return json.substring(cursor, end).trim();
	}

	private record ApprovalResult(String entityType, String entityId) {
	}
}
