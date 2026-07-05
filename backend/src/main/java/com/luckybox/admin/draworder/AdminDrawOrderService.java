package com.luckybox.admin.draworder;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.common.ApiException;

@Service
class AdminDrawOrderService {

	private static final Set<String> STATUSES = Set.of("PENDING", "COMPLETED", "FAILED", "REFUNDED");

	private final AdminDrawOrderRepository adminDrawOrderRepository;

	AdminDrawOrderService(AdminDrawOrderRepository adminDrawOrderRepository) {
		this.adminDrawOrderRepository = adminDrawOrderRepository;
	}

	List<AdminDrawOrderResponse> drawOrders(String status, String campaignSlug, String keyword) {
		requireAdmin();
		return adminDrawOrderRepository.findOrders(
				normalizeOptional(status),
				normalizeText(campaignSlug),
				normalizeText(keyword));
	}

	AdminDrawOrderDetailResponse drawOrder(long orderId) {
		requireAdmin();
		return adminDrawOrderRepository.findOrder(orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"DRAW_ORDER_NOT_FOUND", "找不到指定抽賞訂單。"));
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}

	private static String normalizeOptional(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		String normalized = status.trim().toUpperCase();
		if (!STATUSES.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DRAW_ORDER_STATUS", "抽賞訂單狀態不正確。");
		}
		return normalized;
	}

	private static String normalizeText(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
