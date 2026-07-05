package com.luckybox.wallet;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class LinePaySignature {

	private LinePaySignature() {
	}

	static String post(String channelSecret, String apiPath, String requestBody, String nonce) {
		return sign(channelSecret, channelSecret + apiPath + requestBody + nonce);
	}

	private static String sign(String channelSecret, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to sign LINE Pay request", ex);
		}
	}
}
