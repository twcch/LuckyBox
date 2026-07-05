package com.luckybox.wallet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;

@Service
class EcpayPaymentService {

	private static final String PROVIDER = "ECPAY";
	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final DateTimeFormatter ECPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	private static final Pattern CREDIT_INSTALLMENT_PATTERN =
			Pattern.compile("(30N|(?:3|6|12|18|24)(?:,(?:3|6|12|18|24))*)");

	private final WalletRepository walletRepository;
	private final PaymentWebhookRepository paymentWebhookRepository;
	private final WalletService walletService;
	private final boolean enabled;
	private final String merchantId;
	private final String hashKey;
	private final String hashIv;
	private final String actionUrl;
	private final String returnUrl;
	private final String clientBackUrl;
	private final String choosePayment;
	private final String creditInstallment;
	private final String appBaseUrl;
	private final boolean acceptSimulated;

	EcpayPaymentService(
			WalletRepository walletRepository,
			PaymentWebhookRepository paymentWebhookRepository,
			WalletService walletService,
			@Value("${luckybox.payment.ecpay.enabled:false}") boolean enabled,
			@Value("${luckybox.payment.ecpay.merchant-id:}") String merchantId,
			@Value("${luckybox.payment.ecpay.hash-key:}") String hashKey,
			@Value("${luckybox.payment.ecpay.hash-iv:}") String hashIv,
			@Value("${luckybox.payment.ecpay.action-url:https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5}") String actionUrl,
			@Value("${luckybox.payment.ecpay.return-url:}") String returnUrl,
			@Value("${luckybox.payment.ecpay.client-back-url:}") String clientBackUrl,
			@Value("${luckybox.payment.ecpay.choose-payment:Credit}") String choosePayment,
			@Value("${luckybox.payment.ecpay.credit-installment:}") String creditInstallment,
			@Value("${luckybox.app.base-url:http://localhost:5173}") String appBaseUrl,
			@Value("${luckybox.payment.ecpay.accept-simulated:false}") boolean acceptSimulated) {
		this.walletRepository = walletRepository;
		this.paymentWebhookRepository = paymentWebhookRepository;
		this.walletService = walletService;
		this.enabled = enabled;
		this.merchantId = merchantId == null ? "" : merchantId.trim();
		this.hashKey = hashKey == null ? "" : hashKey.trim();
		this.hashIv = hashIv == null ? "" : hashIv.trim();
		this.actionUrl = blankToDefault(actionUrl, "https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5");
		this.returnUrl = returnUrl == null ? "" : returnUrl.trim();
		this.clientBackUrl = clientBackUrl == null ? "" : clientBackUrl.trim();
		this.choosePayment = blankToDefault(choosePayment, "Credit");
		this.creditInstallment = normalizeCreditInstallment(creditInstallment);
		this.appBaseUrl = trimTrailingSlash(blankToDefault(appBaseUrl, "http://localhost:5173"));
		this.acceptSimulated = acceptSimulated;
	}

	@Transactional
	PaymentCheckoutResponse checkout(long orderId) {
		ensureConfigured();
		long userId = currentUserId();
		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		if (!PROVIDER.equalsIgnoreCase(order.provider())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_UNSUPPORTED", "此付款訂單不是 ECPay 訂單。");
		}
		if (!"PENDING".equals(order.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_PAYABLE", "此付款訂單目前不可前往付款。");
		}

		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("MerchantID", merchantId);
		fields.put("MerchantTradeNo", order.merchantTradeNo());
		fields.put("MerchantTradeDate", ECPAY_DATE_FORMAT.format(Instant.now().atZone(TAIPEI)));
		fields.put("PaymentType", "aio");
		fields.put("TotalAmount", String.valueOf(order.amount()));
		fields.put("TradeDesc", "LuckyBox LP top up");
		fields.put("ItemName", "LuckyBox LP");
		fields.put("ReturnURL", effectiveReturnUrl());
		fields.put("ChoosePayment", choosePayment);
		fields.put("ClientBackURL", effectiveClientBackUrl());
		fields.put("CustomField1", String.valueOf(order.id()));
		if (!creditInstallment.isBlank()) {
			fields.put("CreditInstallment", creditInstallment);
		}
		fields.put("EncryptType", "1");
		fields.put("CheckMacValue", EcpayChecksum.generate(fields, hashKey, hashIv));

		walletRepository.updateProviderPayload(PROVIDER, order.merchantTradeNo(), rawPayload(fields));
		return new PaymentCheckoutResponse(order.id(), PROVIDER, order.merchantTradeNo(), actionUrl, "POST", fields);
	}

	@Transactional
	String handleCallback(MultiValueMap<String, String> formValues) {
		if (!isConfigured()) {
			return "0|ECPAY_NOT_CONFIGURED";
		}
		Map<String, String> form = flatten(formValues);
		if (!merchantId.equals(form.get("MerchantID"))) {
			return "0|MERCHANT_ID_MISMATCH";
		}
		if (!EcpayChecksum.verify(form, hashKey, hashIv)) {
			return "0|WEBHOOK_SIGNATURE_INVALID";
		}

		String merchantTradeNo = blankToNull(form.get("MerchantTradeNo"));
		String eventId = blankToNull(form.get("TradeNo"));
		if (merchantTradeNo == null) {
			return "0|MERCHANT_TRADE_NO_REQUIRED";
		}
		if (eventId == null) {
			eventId = "ECPAY-" + merchantTradeNo + "-" + blankToDefault(form.get("RtnCode"), "UNKNOWN");
		}

		int amount;
		try {
			amount = Integer.parseInt(blankToDefault(form.get("TradeAmt"), "-1"));
		}
		catch (NumberFormatException ex) {
			return "0|TRADE_AMT_INVALID";
		}
		if (amount < 0) {
			return "0|TRADE_AMT_INVALID";
		}

		String status = "1".equals(form.get("RtnCode")) ? "PAID" : "FAILED";
		String rawPayload = rawPayload(form);
		boolean inserted = paymentWebhookRepository.insertEvent(PROVIDER, eventId, merchantTradeNo, status, amount, rawPayload);
		if (!inserted) {
			return "1|OK";
		}

		WalletRepository.PaymentOrderRow order = walletRepository.findPaymentOrderByMerchantTradeNo(PROVIDER, merchantTradeNo)
				.orElse(null);
		if (order == null) {
			paymentWebhookRepository.markRejected(PROVIDER, eventId, "PAYMENT_ORDER_NOT_FOUND");
			return "0|PAYMENT_ORDER_NOT_FOUND";
		}
		if (order.amount() != amount) {
			paymentWebhookRepository.markRejected(PROVIDER, eventId, "AMOUNT_MISMATCH");
			return "0|AMOUNT_MISMATCH";
		}
		if ("1".equals(form.get("SimulatePaid")) && !acceptSimulated) {
			paymentWebhookRepository.markRejected(PROVIDER, eventId, "ECPAY_SIMULATED_PAYMENT_IGNORED");
			walletRepository.updateProviderPayload(PROVIDER, merchantTradeNo, rawPayload);
			return "1|OK";
		}

		if ("PAID".equals(status)) {
			walletService.completePaymentOrderFromWebhook(PROVIDER, merchantTradeNo, rawPayload);
		}
		else {
			walletService.markPaymentOrderFromWebhook(PROVIDER, merchantTradeNo, "FAILED", rawPayload);
		}
		paymentWebhookRepository.markProcessed(PROVIDER, eventId, "OK");
		return "1|OK";
	}

	private void ensureConfigured() {
		if (!isConfigured()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"PAYMENT_ECPAY_NOT_CONFIGURED",
					"ECPay 金流尚未完成設定。");
		}
		if (!creditInstallment.isBlank() && !"Credit".equals(choosePayment)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"PAYMENT_ECPAY_INSTALLMENT_REQUIRES_CREDIT",
					"ECPay 信用卡分期必須搭配 ChoosePayment=Credit。");
		}
		if (!creditInstallment.isBlank() && !CREDIT_INSTALLMENT_PATTERN.matcher(creditInstallment).matches()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"PAYMENT_ECPAY_INSTALLMENT_INVALID",
					"ECPay 信用卡分期設定格式不正確。");
		}
	}

	private boolean isConfigured() {
		return enabled
				&& !merchantId.isBlank()
				&& !hashKey.isBlank()
				&& !hashIv.isBlank()
				&& !actionUrl.isBlank();
	}

	private String effectiveReturnUrl() {
		return returnUrl.isBlank() ? appBaseUrl + "/api/webhooks/payment/ecpay" : returnUrl;
	}

	private String effectiveClientBackUrl() {
		return clientBackUrl.isBlank() ? appBaseUrl + "/account/orders" : clientBackUrl;
	}

	private static Map<String, String> flatten(MultiValueMap<String, String> formValues) {
		Map<String, String> form = new TreeMap<>();
		formValues.forEach((key, values) -> form.put(key, values == null || values.isEmpty() ? "" : values.get(0)));
		return form;
	}

	private static String rawPayload(Map<String, String> fields) {
		return fields.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
				.collect(java.util.stream.Collectors.joining(",", "{", "}"));
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

	private static String normalizeCreditInstallment(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.replace(" ", "").trim().toUpperCase();
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
