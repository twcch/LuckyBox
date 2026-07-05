package com.luckybox.wallet;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.luckybox.common.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class LinePayHttpClient implements LinePayClient {

	private static final String REQUEST_PATH = "/v3/payments/request";

	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final String apiBaseUrl;
	private final String channelId;
	private final String channelSecret;
	private final String merchantDeviceProfileId;

	LinePayHttpClient(
			ObjectMapper objectMapper,
			@Value("${luckybox.payment.linepay.api-base-url:https://sandbox-api-pay.line.me}") String apiBaseUrl,
			@Value("${luckybox.payment.linepay.channel-id:}") String channelId,
			@Value("${luckybox.payment.linepay.channel-secret:}") String channelSecret,
			@Value("${luckybox.payment.linepay.merchant-device-profile-id:}") String merchantDeviceProfileId) {
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		this.apiBaseUrl = trimTrailingSlash(blankToDefault(apiBaseUrl, "https://sandbox-api-pay.line.me"));
		this.channelId = channelId == null ? "" : channelId.trim();
		this.channelSecret = channelSecret == null ? "" : channelSecret.trim();
		this.merchantDeviceProfileId = merchantDeviceProfileId == null ? "" : merchantDeviceProfileId.trim();
	}

	@Override
	public LinePayPaymentResult requestPayment(LinePayPaymentRequest request) {
		String body = writeJson(paymentRequestBody(request));
		JsonNode json = post(REQUEST_PATH, body);
		JsonNode info = json.path("info");
		JsonNode paymentUrl = info.path("paymentUrl");
		return new LinePayPaymentResult(
				json.path("returnCode").asText(""),
				json.path("returnMessage").asText(""),
				info.path("transactionId").asText(""),
				paymentUrl.path("web").asText(""),
				paymentUrl.path("app").asText(""),
				json.toString());
	}

	@Override
	public LinePayConfirmResult confirmPayment(String transactionId, int amount, String currency) {
		Map<String, Object> bodyMap = new LinkedHashMap<>();
		bodyMap.put("amount", amount);
		bodyMap.put("currency", currency);
		String body = writeJson(bodyMap);
		JsonNode json = post("/v3/payments/" + transactionId + "/confirm", body);
		return new LinePayConfirmResult(
				json.path("returnCode").asText(""),
				json.path("returnMessage").asText(""),
				json.toString());
	}

	private JsonNode post(String apiPath, String body) {
		String nonce = UUID.randomUUID().toString();
		String signature = LinePaySignature.post(channelSecret, apiPath, body, nonce);
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiBaseUrl + apiPath))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json")
				.header("X-LINE-ChannelId", channelId)
				.header("X-LINE-Authorization-Nonce", nonce)
				.header("X-LINE-Authorization", signature)
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		if (!merchantDeviceProfileId.isBlank()) {
			builder.header("X-LINE-MerchantDeviceProfileId", merchantDeviceProfileId);
		}
		try {
			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"PAYMENT_LINEPAY_HTTP_FAILED",
						"LINE Pay API 呼叫失敗。");
			}
			return objectMapper.readTree(response.body());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"PAYMENT_LINEPAY_HTTP_INTERRUPTED",
					"LINE Pay API 呼叫被中斷。");
		}
		catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"PAYMENT_LINEPAY_HTTP_FAILED",
					"LINE Pay API 呼叫失敗。");
		}
	}

	private Map<String, Object> paymentRequestBody(LinePayPaymentRequest request) {
		Map<String, Object> product = new LinkedHashMap<>();
		product.put("id", "LUCKYBOX-LP");
		product.put("name", request.productName());
		product.put("quantity", 1);
		product.put("price", request.amount());

		Map<String, Object> productPackage = new LinkedHashMap<>();
		productPackage.put("id", "luckybox");
		productPackage.put("amount", request.amount());
		productPackage.put("products", List.of(product));

		Map<String, Object> redirectUrls = new LinkedHashMap<>();
		redirectUrls.put("confirmUrl", request.confirmUrl());
		redirectUrls.put("cancelUrl", request.cancelUrl());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("amount", request.amount());
		body.put("currency", request.currency());
		body.put("orderId", request.orderId());
		body.put("packages", List.of(productPackage));
		body.put("redirectUrls", redirectUrls);
		return body;
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Unable to serialize LINE Pay request", ex);
		}
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String trimTrailingSlash(String value) {
		String result = value;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
