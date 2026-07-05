package com.luckybox.wallet;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;

import tools.jackson.databind.JsonNode;

@Service
class JkoPayPaymentService {

	private static final String PROVIDER = "JKOPAY";

	private final WalletRepository walletRepository;
	private final PaymentWebhookRepository paymentWebhookRepository;
	private final WalletService walletService;
	private final JkoPayClient jkoPayClient;
	private final boolean enabled;
	private final String apiKey;
	private final String secretKey;
	private final String storeId;
	private final String currency;
	private final String confirmUrl;
	private final String resultUrl;
	private final String resultDisplayUrl;
	private final String appBaseUrl;

	JkoPayPaymentService(
			WalletRepository walletRepository,
			PaymentWebhookRepository paymentWebhookRepository,
			WalletService walletService,
			JkoPayClient jkoPayClient,
			@Value("${luckybox.payment.jkopay.enabled:false}") boolean enabled,
			@Value("${luckybox.payment.jkopay.api-key:}") String apiKey,
			@Value("${luckybox.payment.jkopay.secret-key:}") String secretKey,
			@Value("${luckybox.payment.jkopay.store-id:}") String storeId,
			@Value("${luckybox.payment.jkopay.currency:TWD}") String currency,
			@Value("${luckybox.payment.jkopay.confirm-url:}") String confirmUrl,
			@Value("${luckybox.payment.jkopay.result-url:}") String resultUrl,
			@Value("${luckybox.payment.jkopay.result-display-url:}") String resultDisplayUrl,
			@Value("${luckybox.app.base-url:http://localhost:5173}") String appBaseUrl) {
		this.walletRepository = walletRepository;
		this.paymentWebhookRepository = paymentWebhookRepository;
		this.walletService = walletService;
		this.jkoPayClient = jkoPayClient;
		this.enabled = enabled;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.secretKey = secretKey == null ? "" : secretKey.trim();
		this.storeId = storeId == null ? "" : storeId.trim();
		this.currency = blankToDefault(currency, "TWD").toUpperCase();
		this.appBaseUrl = trimTrailingSlash(blankToDefault(appBaseUrl, "http://localhost:5173"));
		this.confirmUrl = blankToDefault(confirmUrl, this.appBaseUrl + "/api/webhooks/payment/jkopay/confirm");
		this.resultUrl = blankToDefault(resultUrl, this.appBaseUrl + "/api/webhooks/payment/jkopay/result");
		this.resultDisplayUrl = blankToDefault(resultDisplayUrl, this.appBaseUrl + "/account/wallet?payment=success");
	}

	@Transactional
	PaymentRedirectResponse checkout(long orderId) {
		ensureConfigured();
		long userId = currentUserId();
		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		if (!PROVIDER.equalsIgnoreCase(order.provider())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_UNSUPPORTED", "此付款訂單不是街口支付訂單。");
		}
		if (!"PENDING".equals(order.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_PAYABLE", "此付款訂單目前不可前往付款。");
		}

		JkoPayEntryResult result = jkoPayClient.createEntry(new JkoPayEntryRequest(
				order.merchantTradeNo(),
				storeId,
				currency,
				order.amount(),
				order.amount(),
				confirmUrl,
				resultUrl,
				resultDisplayUrl,
				"LuckyBox LP"));
		walletRepository.updateProviderPayload(PROVIDER, order.merchantTradeNo(), result.rawPayload());
		if (!"000".equals(result.result()) || result.paymentUrl().isBlank()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"PAYMENT_JKOPAY_REQUEST_FAILED",
					"街口支付建立付款失敗。");
		}
		return new PaymentRedirectResponse(
				order.id(),
				PROVIDER,
				order.merchantTradeNo(),
				result.paymentUrl(),
				null,
				blankToNull(result.qrImageUrl()),
				null,
				Map.of("result", result.result(), "qrTimeout", blankToDefault(result.qrTimeout(), "")));
	}

	@Transactional(readOnly = true)
	JkoPayConfirmResponse confirm(JkoPayConfirmRequest request) {
		String merchantTradeNo = request == null ? "" : blankToDefault(request.platformOrderId(), "");
		boolean valid = walletRepository.findPaymentOrderByMerchantTradeNo(PROVIDER, merchantTradeNo)
				.filter(order -> "PENDING".equals(order.status()))
				.isPresent();
		return new JkoPayConfirmResponse(valid);
	}

	@Transactional
	PaymentWebhookResponse handleResult(JsonNode payload) {
		JsonNode transaction = payload == null ? null : payload.path("transaction");
		if (transaction == null || transaction.isMissingNode()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_JKOPAY_RESULT_INVALID", "街口支付回呼格式不正確。");
		}
		String merchantTradeNo = transaction.path("platform_order_id").asText("");
		String tradeNo = transaction.path("tradeNo").asText("");
		if (merchantTradeNo.isBlank() || tradeNo.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_JKOPAY_RESULT_INVALID", "街口支付回呼缺少交易序號。");
		}
		int amount = parseAmount(transaction.path("final_price").asText(""));
		int statusCode = transaction.path("status").asInt(-1);
		String status = statusCode == 0 ? "PAID" : "FAILED";
		String rawPayload = payload.toString();
		boolean inserted = paymentWebhookRepository.insertEvent(PROVIDER, tradeNo, merchantTradeNo, status, amount, rawPayload);
		if (!inserted) {
			return duplicateResponse(tradeNo);
		}

		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrderByMerchantTradeNo(PROVIDER, merchantTradeNo)
				.orElse(null);
		if (order == null) {
			paymentWebhookRepository.markRejected(PROVIDER, tradeNo, "PAYMENT_ORDER_NOT_FOUND");
			return response(tradeNo, merchantTradeNo, status, false, false, null, "PAYMENT_ORDER_NOT_FOUND");
		}
		if (order.amount() != amount) {
			paymentWebhookRepository.markRejected(PROVIDER, tradeNo, "AMOUNT_MISMATCH");
			return response(tradeNo, merchantTradeNo, status, false, false, order.status(), "AMOUNT_MISMATCH");
		}

		PaymentOrderResponse orderResponse;
		if ("PAID".equals(status)) {
			orderResponse = walletService.completePaymentOrderFromWebhook(PROVIDER, merchantTradeNo, rawPayload);
			paymentWebhookRepository.markProcessed(PROVIDER, tradeNo, "OK");
		}
		else {
			orderResponse = walletService.markPaymentOrderFromWebhook(PROVIDER, merchantTradeNo, "FAILED", rawPayload);
			paymentWebhookRepository.markProcessed(PROVIDER, tradeNo, "JKOPAY_STATUS_" + statusCode);
		}
		return response(tradeNo, merchantTradeNo, status, true, false, orderResponse.status(), "OK");
	}

	boolean isConfigured() {
		return enabled && !apiKey.isBlank() && !secretKey.isBlank() && !storeId.isBlank();
	}

	private void ensureConfigured() {
		if (!isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"PAYMENT_JKOPAY_NOT_CONFIGURED",
					"街口支付尚未完成設定。");
		}
	}

	private static int parseAmount(String value) {
		try {
			return Integer.parseInt(value == null ? "" : value.trim());
		}
		catch (NumberFormatException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_JKOPAY_AMOUNT_INVALID", "街口支付回呼金額不正確。");
		}
	}

	private static String trimTrailingSlash(String value) {
		String result = value;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private PaymentWebhookResponse duplicateResponse(String eventId) {
		PaymentWebhookRepository.WebhookEventRow event = paymentWebhookRepository.findEvent(PROVIDER, eventId)
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WEBHOOK_EVENT_NOT_FOUND", "找不到 webhook event。"));
		String orderStatus = walletRepository.findPaymentOrderByMerchantTradeNo(PROVIDER, event.merchantTradeNo())
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
			String eventId,
			String merchantTradeNo,
			String status,
			boolean processed,
			boolean duplicate,
			String orderStatus,
			String message) {
		return new PaymentWebhookResponse(
				PROVIDER,
				eventId,
				merchantTradeNo,
				status,
				processed,
				duplicate,
				orderStatus,
				message);
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
