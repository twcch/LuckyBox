package com.luckybox.admin.approval;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.luckybox.admin.payment.RefundRequest;
import com.luckybox.admin.user.CompensationRequest;
import com.luckybox.admin.walletledger.WalletAdjustmentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/approval-requests")
class AdminApprovalController {

	private final AdminApprovalService adminApprovalService;

	AdminApprovalController(AdminApprovalService adminApprovalService) {
		this.adminApprovalService = adminApprovalService;
	}

	@GetMapping
	List<AdminApprovalRequestResponse> requests(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type) {
		return adminApprovalService.requests(status, type);
	}

	@PostMapping("/wallet-adjustments")
	@ResponseStatus(HttpStatus.CREATED)
	AdminApprovalRequestResponse requestWalletAdjustment(@Valid @RequestBody WalletAdjustmentRequest request) {
		return adminApprovalService.requestWalletAdjustment(request);
	}

	@PostMapping("/payment-refunds/{orderId}")
	@ResponseStatus(HttpStatus.CREATED)
	AdminApprovalRequestResponse requestPaymentRefund(
			@PathVariable long orderId,
			@Valid @RequestBody RefundRequest request) {
		return adminApprovalService.requestPaymentRefund(orderId, request);
	}

	@PostMapping("/compensations/{userId}")
	@ResponseStatus(HttpStatus.CREATED)
	AdminApprovalRequestResponse requestCompensation(
			@PathVariable long userId,
			@Valid @RequestBody CompensationRequest request) {
		return adminApprovalService.requestCompensation(userId, request);
	}

	@PostMapping("/{requestId}/approve")
	AdminApprovalRequestResponse approve(@PathVariable long requestId) {
		return adminApprovalService.approve(requestId);
	}

	@PostMapping("/{requestId}/reject")
	AdminApprovalRequestResponse reject(
			@PathVariable long requestId,
			@Valid @RequestBody(required = false) AdminApprovalReviewRequest request) {
		return adminApprovalService.reject(requestId, request);
	}
}
