package com.luckybox.wallet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.common.ApiException;

@Service
class PaymentWebhookService {

	private static final String MOCK_PROVIDER = "MOCK";

	private final PaymentWebhookRepository paymentWebhookRepository;
	private final WalletRepository walletRepository;
	private final WalletService walletService;
	private final String mockWebhookSecret;
	private final boolean mockPaymentEnabled;

	PaymentWebhookService(
			PaymentWebhookRepository paymentWebhookRepository,
			WalletRepository walletRepository,
			WalletService walletService,
			@Value("${luckybox.payment.mock-webhook-secret:dev-mock-webhook-secret}") String mockWebhookSecret,
			@Value("${luckybox.payment.mock-enabled:true}") boolean mockPaymentEnabled) {
		this.paymentWebhookRepository = paymentWebhookRepository;
		this.walletRepository = walletRepository;
		this.walletService = walletService;
		this.mockWebhookSecret = mockWebhookSecret;
		this.mockPaymentEnabled = mockPaymentEnabled;
	}

	@Transactional
	PaymentWebhookResponse handle(String provider, PaymentWebhookRequest request, String signature) {
		String normalizedProvider = normalizeProvider(provider);
		if (!mockPaymentEnabled) {
			throw mockPaymentDisabled();
		}
		verifySignature(request, signature);
		String rawPayload = rawPayload(normalizedProvider, request);
		boolean inserted = paymentWebhookRepository.insertEvent(normalizedProvider, request, rawPayload);
		if (!inserted) {
			return duplicateResponse(normalizedProvider, request.eventId());
		}

		WalletRepository.PaymentOrderRow order = walletRepository
				.findPaymentOrderByMerchantTradeNo(normalizedProvider, request.merchantTradeNo())
				.orElse(null);
		if (order == null) {
			paymentWebhookRepository.markRejected(normalizedProvider, request.eventId(), "PAYMENT_ORDER_NOT_FOUND");
			return response(normalizedProvider, request, false, false, null, "PAYMENT_ORDER_NOT_FOUND");
		}
		if (order.amount() != request.amount()) {
			paymentWebhookRepository.markRejected(normalizedProvider, request.eventId(), "AMOUNT_MISMATCH");
			return response(normalizedProvider, request, false, false, order.status(), "AMOUNT_MISMATCH");
		}

		PaymentOrderResponse orderResponse = switch (request.status()) {
			case "PAID" -> walletService.completePaymentOrderFromWebhook(
					normalizedProvider, request.merchantTradeNo(), rawPayload);
			case "FAILED", "CANCELED" -> walletService.markPaymentOrderFromWebhook(
					normalizedProvider, request.merchantTradeNo(), request.status(), rawPayload);
			default -> throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_WEBHOOK_STATUS_UNSUPPORTED", "付款狀態不支援。");
		};
		paymentWebhookRepository.markProcessed(normalizedProvider, request.eventId(), "OK");
		return response(normalizedProvider, request, true, false, orderResponse.status(), "OK");
	}

	private String normalizeProvider(String provider) {
		String normalized = provider == null ? "" : provider.trim().toUpperCase();
		if (!MOCK_PROVIDER.equals(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_UNSUPPORTED", "此 JSON webhook endpoint 只支援 mock provider。");
		}
		return normalized;
	}

	private void verifySignature(PaymentWebhookRequest request, String signature) {
		String expected = PaymentWebhookSignature.sign(mockWebhookSecret, request.signaturePayload());
		if (signature == null || signature.isBlank() || !PaymentWebhookSignature.matches(expected, signature.trim())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_SIGNATURE_INVALID", "金流 webhook 簽章不正確。");
		}
	}

	private static ApiException mockPaymentDisabled() {
		return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_MOCK_DISABLED", "Mock 金流功能未啟用。");
	}

	private PaymentWebhookResponse duplicateResponse(String provider, String eventId) {
		PaymentWebhookRepository.WebhookEventRow event = paymentWebhookRepository.findEvent(provider, eventId)
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WEBHOOK_EVENT_NOT_FOUND", "找不到 webhook event。"));
		String orderStatus = walletRepository.findPaymentOrderByMerchantTradeNo(provider, event.merchantTradeNo())
				.map(WalletRepository.PaymentOrderRow::status)
				.orElse(null);
		return new PaymentWebhookResponse(
				event.provider(),
				event.eventId(),
				event.merchantTradeNo(),
				event.status(),
				event.processed(),
				true,
				orderStatus,
				event.message());
	}

	private static PaymentWebhookResponse response(
			String provider,
			PaymentWebhookRequest request,
			boolean processed,
			boolean duplicate,
			String orderStatus,
			String message) {
		return new PaymentWebhookResponse(
				provider,
				request.eventId(),
				request.merchantTradeNo(),
				request.status(),
				processed,
				duplicate,
				orderStatus,
				message);
	}

	private static String rawPayload(String provider, PaymentWebhookRequest request) {
		return """
				{"provider":"%s","eventId":"%s","merchantTradeNo":"%s","amount":%d,"status":"%s","occurredAt":"%s"}
				""".formatted(
				escape(provider),
				escape(request.eventId()),
				escape(request.merchantTradeNo()),
				request.amount(),
				escape(request.status()),
				escape(request.occurredAt() == null ? "" : request.occurredAt()));
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
