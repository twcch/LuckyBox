package com.luckybox.wallet;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;

@Service
class MockPaymentCheckoutService {

	private static final String MOCK_PROVIDER = "MOCK";

	private final WalletRepository walletRepository;
	private final PaymentWebhookService paymentWebhookService;
	private final String mockWebhookSecret;
	private final boolean mockPaymentEnabled;

	MockPaymentCheckoutService(
			WalletRepository walletRepository,
			PaymentWebhookService paymentWebhookService,
			@Value("${luckybox.payment.mock-webhook-secret:dev-mock-webhook-secret}") String mockWebhookSecret,
			@Value("${luckybox.payment.mock-enabled:true}") boolean mockPaymentEnabled) {
		this.walletRepository = walletRepository;
		this.paymentWebhookService = paymentWebhookService;
		this.mockWebhookSecret = mockWebhookSecret;
		this.mockPaymentEnabled = mockPaymentEnabled;
	}

	PaymentOrderResponse confirm(long orderId) {
		long userId = currentUserId();
		if (!mockPaymentEnabled) {
			throw mockPaymentDisabled();
		}
		WalletRepository.PaymentOrderRow currentOrder = walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		if ("PAID".equals(currentOrder.status())) {
			return currentOrder.toResponse();
		}
		if (!"PENDING".equals(currentOrder.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_PAYABLE", "此付款訂單目前不可完成付款。");
		}
		if (!MOCK_PROVIDER.equalsIgnoreCase(currentOrder.provider())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_UNSUPPORTED", "此付款供應商不支援 mock checkout。");
		}

		PaymentWebhookRequest request = new PaymentWebhookRequest(
				"mock-checkout-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 12),
				currentOrder.merchantTradeNo(),
				currentOrder.amount(),
				"PAID",
				Instant.now().toString());
		String signature = PaymentWebhookSignature.sign(mockWebhookSecret, request.signaturePayload());
		paymentWebhookService.handle(MOCK_PROVIDER, request, signature);
		return walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"))
				.toResponse();
	}

	private static ApiException mockPaymentDisabled() {
		return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_MOCK_DISABLED", "Mock 金流功能未啟用。");
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
