package com.luckybox.wallet;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
class WalletCompatibilityController {

	private final WalletService walletService;

	WalletCompatibilityController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping("/wallet")
	WalletOverviewResponse wallet() {
		return walletService.overview();
	}

	@GetMapping("/wallet/ledger")
	List<WalletLedgerResponse> ledger() {
		return walletService.ledger();
	}

	@PostMapping("/payments/top-up")
	@ResponseStatus(HttpStatus.CREATED)
	PaymentOrderResponse createPaymentOrder(@Valid @RequestBody CreatePaymentOrderRequest request) {
		return walletService.createPaymentOrder(request);
	}

	@PostMapping("/payments/mock/complete")
	PaymentOrderResponse completeMockPayment(@Valid @RequestBody PaymentMockCompleteRequest request) {
		return walletService.completePaymentOrder(request.orderId());
	}
}
