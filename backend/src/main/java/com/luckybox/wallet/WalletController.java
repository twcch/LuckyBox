package com.luckybox.wallet;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
class WalletController {

	private final WalletService walletService;
	private final MockPaymentCheckoutService mockPaymentCheckoutService;
	private final EcpayPaymentService ecpayPaymentService;
	private final LinePayPaymentService linePayPaymentService;
	private final JkoPayPaymentService jkoPayPaymentService;

	WalletController(
			WalletService walletService,
			MockPaymentCheckoutService mockPaymentCheckoutService,
			EcpayPaymentService ecpayPaymentService,
			LinePayPaymentService linePayPaymentService,
			JkoPayPaymentService jkoPayPaymentService) {
		this.walletService = walletService;
		this.mockPaymentCheckoutService = mockPaymentCheckoutService;
		this.ecpayPaymentService = ecpayPaymentService;
		this.linePayPaymentService = linePayPaymentService;
		this.jkoPayPaymentService = jkoPayPaymentService;
	}

	@GetMapping("/wallet")
	WalletOverviewResponse wallet() {
		return walletService.overview();
	}

	@GetMapping("/wallet/ledger")
	List<WalletLedgerResponse> ledger() {
		return walletService.ledger();
	}

	@GetMapping("/wallet/top-up-plans")
	List<TopUpPlanResponse> topUpPlans() {
		return walletService.topUpPlans();
	}

	@PostMapping("/payment-orders")
	@ResponseStatus(HttpStatus.CREATED)
	PaymentOrderResponse createPaymentOrder(@Valid @RequestBody CreatePaymentOrderRequest request) {
		return walletService.createPaymentOrder(request);
	}

	@PostMapping("/payment-orders/{orderId}/complete")
	PaymentOrderResponse completePaymentOrder(@PathVariable long orderId) {
		return walletService.completePaymentOrder(orderId);
	}

	@PostMapping("/payment-orders/{orderId}/mock-checkout/confirm")
	PaymentOrderResponse confirmMockCheckout(@PathVariable long orderId) {
		return mockPaymentCheckoutService.confirm(orderId);
	}

	@PostMapping("/payment-orders/{orderId}/ecpay-checkout")
	PaymentCheckoutResponse createEcpayCheckout(@PathVariable long orderId) {
		return ecpayPaymentService.checkout(orderId);
	}

	@PostMapping("/payment-orders/{orderId}/linepay-checkout")
	PaymentRedirectResponse createLinePayCheckout(@PathVariable long orderId) {
		return linePayPaymentService.checkout(orderId);
	}

	@PostMapping("/payment-orders/{orderId}/jkopay-checkout")
	PaymentRedirectResponse createJkoPayCheckout(@PathVariable long orderId) {
		return jkoPayPaymentService.checkout(orderId);
	}
}
