package com.luckybox.admin.payment;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
public class AdminPaymentOrderService {

	private static final Set<String> STATUSES = Set.of("PENDING", "PAID", "FAILED", "CANCELED", "REFUNDED");
	private static final int REASON_MAX_LENGTH = 200;

	private final AdminPaymentOrderRepository adminPaymentOrderRepository;
	private final AuditLogHelper auditLogHelper;

	AdminPaymentOrderService(AdminPaymentOrderRepository adminPaymentOrderRepository, AuditLogHelper auditLogHelper) {
		this.adminPaymentOrderRepository = adminPaymentOrderRepository;
		this.auditLogHelper = auditLogHelper;
	}

	List<AdminPaymentOrderResponse> paymentOrders(String status, String provider, String keyword) {
		requireAdmin();
		return adminPaymentOrderRepository.findPaymentOrders(
				normalizeStatus(status),
				normalizeText(provider),
				normalizeText(keyword));
	}

	AdminPaymentOrderDetailResponse paymentOrder(long orderId) {
		requireAdmin();
		AdminPaymentOrderResponse order = adminPaymentOrderRepository.findResponse(orderId);
		if (order == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。");
		}
		return new AdminPaymentOrderDetailResponse(
				order,
				adminPaymentOrderRepository.findProviderPayload(orderId),
				adminPaymentOrderRepository.findWebhookEvents(order.provider(), order.merchantTradeNo()));
	}

	@Transactional
	public AdminPaymentOrderResponse refundPaymentOrder(long orderId, RefundRequest request) {
		SessionPrincipal admin = requireAdmin();
		String reason = request == null || request.reason() == null ? "" : request.reason().strip();
		if (reason.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_REASON_REQUIRED", "請填寫退款原因以利稽核。");
		}
		if (reason.length() > REASON_MAX_LENGTH) {
			reason = reason.substring(0, REASON_MAX_LENGTH);
		}
		AdminPaymentOrderRepository.RefundRow order = adminPaymentOrderRepository.findRefundRow(orderId);
		if (order == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。");
		}
		if (!"PAID".equals(order.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_REFUNDABLE", "只有已付款訂單可以退款。");
		}
		// 條件式轉 REFUNDED 防併發重複退款；隨後回收此訂單原先入帳的現金點與紅利點（交易內任一失敗即整筆回滾）。
		if (!adminPaymentOrderRepository.markRefunded(orderId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_REFUNDABLE", "只有已付款訂單可以退款。");
		}
		if (!adminPaymentOrderRepository.reverseWalletCredit(
				order.userId(), "CASH", order.pointAmount(), orderId, reason, admin.user().id())
				|| !adminPaymentOrderRepository.reverseWalletCredit(
						order.userId(), "BONUS", order.bonusPointAmount(), orderId, reason, admin.user().id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_INSUFFICIENT_BALANCE",
					"會員點數餘額不足以回收（可能已使用），請改以人工點數調整處理。");
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_PAYMENT_REFUNDED",
				"PaymentOrder",
				String.valueOf(orderId),
				"{\"cash\":" + order.pointAmount() + ",\"bonus\":" + order.bonusPointAmount() + "}");
		return adminPaymentOrderRepository.findResponse(orderId);
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private static String normalizeStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		String normalized = status.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_ORDER_STATUS", "付款訂單狀態不正確。");
		}
		return normalized;
	}

	private static String normalizeText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
