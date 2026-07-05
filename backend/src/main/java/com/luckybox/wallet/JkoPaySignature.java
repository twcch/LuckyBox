package com.luckybox.wallet;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class JkoPaySignature {

	private JkoPaySignature() {
	}

	static String digest(String payload, String secretKey) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte current : bytes) {
				hex.append(String.format("%02x", current & 0xff));
			}
			return hex.toString();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to sign JKo Pay request", ex);
		}
	}
}
