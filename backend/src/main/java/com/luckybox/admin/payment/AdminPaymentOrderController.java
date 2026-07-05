package com.luckybox.admin.payment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/payment-orders")
class AdminPaymentOrderController {

	private final AdminPaymentOrderService adminPaymentOrderService;

	AdminPaymentOrderController(AdminPaymentOrderService adminPaymentOrderService) {
		this.adminPaymentOrderService = adminPaymentOrderService;
	}

	@GetMapping
	List<AdminPaymentOrderResponse> paymentOrders(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String provider,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminPaymentOrderService.paymentOrders(status, provider, keyword);
	}

	@GetMapping("/{orderId}")
	AdminPaymentOrderDetailResponse paymentOrder(@PathVariable long orderId) {
		return adminPaymentOrderService.paymentOrder(orderId);
	}

	@PostMapping("/{orderId}/refund")
	AdminPaymentOrderResponse refund(@PathVariable long orderId, @Valid @RequestBody RefundRequest request) {
		return adminPaymentOrderService.refundPaymentOrder(orderId, request);
	}
}
