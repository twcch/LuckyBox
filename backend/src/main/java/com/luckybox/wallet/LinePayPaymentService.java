package com.luckybox.wallet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.view.RedirectView;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;

@Service
class LinePayPaymentService {

	private static final String PROVIDER = "LINEPAY";

	private final WalletRepository walletRepository;
	private final PaymentWebhookRepository paymentWebhookRepository;
	private final WalletService walletService;
	private final LinePayClient linePayClient;
	private final boolean enabled;
	private final String channelId;
	private final String channelSecret;
	private final String currency;
	private final String appBaseUrl;

	LinePayPaymentService(
			WalletRepository walletRepository,
			PaymentWebhookRepository paymentWebhookRepository,
			WalletService walletService,
			LinePayClient linePayClient,
			@Value("${luckybox.payment.linepay.enabled:false}") boolean enabled,
			@Value("${luckybox.payment.linepay.channel-id:}") String channelId,
			@Value("${luckybox.payment.linepay.channel-secret:}") String channelSecret,
			@Value("${luckybox.payment.linepay.currency:TWD}") String currency,
			@Value("${luckybox.app.base-url:http://localhost:5173}") String appBaseUrl) {
		this.walletRepository = walletRepository;
		this.paymentWebhookRepository = paymentWebhookRepository;
		this.walletService = walletService;
		this.linePayClient = linePayClient;
		this.enabled = enabled;
		this.channelId = channelId == null ? "" : channelId.trim();
		this.channelSecret = channelSecret == null ? "" : channelSecret.trim();
		this.currency = blankToDefault(currency, "TWD").toUpperCase();
		this.appBaseUrl = trimTrailingSlash(blankToDefault(appBaseUrl, "http://localhost:5173"));
	}

	@Transactional
	PaymentRedirectResponse checkout(long orderId) {
		ensureConfigured();
		long userId = currentUserId();
		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		if (!PROVIDER.equalsIgnoreCase(order.provider())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_UNSUPPORTED", "此付款訂單不是 LINE Pay 訂單。");
		}
		if (!"PENDING".equals(order.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_PAYABLE", "此付款訂單目前不可前往付款。");
		}

		LinePayPaymentResult result = linePayClient.requestPayment(new LinePayPaymentRequest(
				order.merchantTradeNo(),
				order.amount(),
				currency,
				"LuckyBox LP",
				appBaseUrl + "/api/webhooks/payment/linepay/confirm/" + urlEncode(order.merchantTradeNo()),
				appBaseUrl + "/api/webhooks/payment/linepay/cancel/" + urlEncode(order.merchantTradeNo())));
		walletRepository.updateProviderPayload(PROVIDER, order.merchantTradeNo(), result.rawPayload());
		if (!"0000".equals(result.returnCode()) || result.webPaymentUrl().isBlank()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"PAYMENT_LINEPAY_REQUEST_FAILED",
					"LINE Pay 建立付款失敗。");
		}
		return new PaymentRedirectResponse(
				order.id(),
				PROVIDER,
				order.merchantTradeNo(),
				result.webPaymentUrl(),
				blankToNull(result.appPaymentUrl()),
				null,
				blankToNull(result.transactionId()),
				Map.of("returnCode", result.returnCode()));
	}

	@Transactional
	RedirectView confirm(String merchantTradeNo, String transactionId) {
		ensureConfigured();
		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrderByMerchantTradeNo(PROVIDER, merchantTradeNo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		String eventId = "LINEPAY-" + blankToDefault(transactionId, merchantTradeNo);
		PaymentWebhookRepository.WebhookEventRow existingEvent = paymentWebhookRepository.findEvent(PROVIDER, eventId).orElse(null);
		if (existingEvent != null) {
			return new RedirectView(appBaseUrl + "/account/wallet?payment=" + ("PAID".equals(existingEvent.status()) ? "success" : "failed"));
		}
		LinePayConfirmResult result = linePayClient.confirmPayment(transactionId, order.amount(), currency);
		String rawPayload = result.rawPayload();
		boolean success = "0000".equals(result.returnCode());
		boolean inserted = paymentWebhookRepository.insertEvent(
				PROVIDER,
				eventId,
				merchantTradeNo,
				success ? "PAID" : "FAILED",
				order.amount(),
				rawPayload);
		if (inserted) {
			if (success) {
				walletService.completePaymentOrderFromWebhook(PROVIDER, merchantTradeNo, rawPayload);
				paymentWebhookRepository.markProcessed(PROVIDER, eventId, "OK");
			}
			else {
				walletService.markPaymentOrderFromWebhook(PROVIDER, merchantTradeNo, "FAILED", rawPayload);
				paymentWebhookRepository.markRejected(PROVIDER, eventId, "LINEPAY_" + blankToDefault(result.returnCode(), "FAILED"));
			}
		}
		return new RedirectView(appBaseUrl + "/account/wallet?payment=" + (success ? "success" : "failed"));
	}

	@Transactional
	RedirectView cancel(String merchantTradeNo) {
		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrderByMerchantTradeNo(PROVIDER, merchantTradeNo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		String eventId = "LINEPAY-CANCEL-" + merchantTradeNo;
		String rawPayload = "{\"provider\":\"LINEPAY\",\"merchantTradeNo\":\"" + escape(merchantTradeNo) + "\",\"status\":\"CANCELED\"}";
		boolean inserted = paymentWebhookRepository.insertEvent(PROVIDER, eventId, merchantTradeNo, "CANCELED", order.amount(), rawPayload);
		if (inserted) {
			walletService.markPaymentOrderFromWebhook(PROVIDER, merchantTradeNo, "CANCELED", rawPayload);
			paymentWebhookRepository.markProcessed(PROVIDER, eventId, "CANCELED");
		}
		return new RedirectView(appBaseUrl + "/account/wallet?payment=canceled");
	}

	boolean isConfigured() {
		return enabled && !channelId.isBlank() && !channelSecret.isBlank();
	}

	private void ensureConfigured() {
		if (!isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"PAYMENT_LINEPAY_NOT_CONFIGURED",
					"LINE Pay 金流尚未完成設定。");
		}
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String escape(String value) {
		return (value == null ? "" : value)
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
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

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
