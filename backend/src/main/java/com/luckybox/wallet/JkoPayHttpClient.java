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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.luckybox.common.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class JkoPayHttpClient implements JkoPayClient {

	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final String entryUrl;
	private final String apiKey;
	private final String secretKey;

	JkoPayHttpClient(
			ObjectMapper objectMapper,
			@Value("${luckybox.payment.jkopay.entry-url:https://test-onlinepay.jkopay.app/platform/entry}") String entryUrl,
			@Value("${luckybox.payment.jkopay.api-key:}") String apiKey,
			@Value("${luckybox.payment.jkopay.secret-key:}") String secretKey) {
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		this.entryUrl = blankToDefault(entryUrl, "https://test-onlinepay.jkopay.app/platform/entry");
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.secretKey = secretKey == null ? "" : secretKey.trim();
	}

	@Override
	public JkoPayEntryResult createEntry(JkoPayEntryRequest request) {
		String body = writeJson(entryBody(request));
		HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(entryUrl))
				.timeout(Duration.ofSeconds(20))
				.header("Content-Type", "application/json")
				.header("api-key", apiKey)
				.header("digest", JkoPaySignature.digest(body, secretKey))
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
		try {
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"PAYMENT_JKOPAY_HTTP_FAILED",
						"街口支付 API 呼叫失敗。");
			}
			JsonNode json = objectMapper.readTree(response.body());
			JsonNode resultObject = json.path("result_object");
			return new JkoPayEntryResult(
					json.path("result").asText(""),
					json.path("message").asText(""),
					resultObject.path("payment_url").asText(""),
					resultObject.path("qr_img").asText(""),
					resultObject.path("qr_timeout").asText(""),
					json.toString());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"PAYMENT_JKOPAY_HTTP_INTERRUPTED",
					"街口支付 API 呼叫被中斷。");
		}
		catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"PAYMENT_JKOPAY_HTTP_FAILED",
					"街口支付 API 呼叫失敗。");
		}
	}

	private Map<String, Object> entryBody(JkoPayEntryRequest request) {
		Map<String, Object> product = new LinkedHashMap<>();
		product.put("name", request.productName());
		product.put("unit_count", 1);
		product.put("unit_price", request.totalPrice());
		product.put("unit_final_price", request.finalPrice());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("platform_order_id", request.platformOrderId());
		body.put("store_id", request.storeId());
		body.put("currency", request.currency());
		body.put("total_price", request.totalPrice());
		body.put("final_price", request.finalPrice());
		if (request.confirmUrl() != null && !request.confirmUrl().isBlank()) {
			body.put("confirm_url", request.confirmUrl());
		}
		body.put("result_url", request.resultUrl());
		body.put("result_display_url", request.resultDisplayUrl());
		body.put("payment_type", "onetime");
		body.put("escrow", false);
		body.put("products", List.of(product));
		return body;
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Unable to serialize JKo Pay request", ex);
		}
	}

	private static String blankToDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}
