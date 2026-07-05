package com.luckybox.wallet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class EcpayChecksum {

	private EcpayChecksum() {
	}

	static String generate(Map<String, String> parameters, String hashKey, String hashIv) {
		String sorted = parameters.entrySet().stream()
				.filter(entry -> !"CheckMacValue".equalsIgnoreCase(entry.getKey()))
				.sorted(Comparator.comparing(entry -> entry.getKey().toLowerCase(Locale.ROOT)))
				.map(entry -> entry.getKey() + "=" + nullToEmpty(entry.getValue()))
				.collect(Collectors.joining("&"));
		String raw = "HashKey=" + hashKey + "&" + sorted + "&HashIV=" + hashIv;
		String encoded = ecpayUrlEncode(raw).toLowerCase(Locale.ROOT);
		return sha256Hex(encoded).toUpperCase(Locale.ROOT);
	}

	static boolean verify(Map<String, String> parameters, String hashKey, String hashIv) {
		String provided = parameters.get("CheckMacValue");
		if (provided == null || provided.isBlank()) {
			return false;
		}
		String expected = generate(parameters, hashKey, hashIv);
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				provided.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
	}

	private static String ecpayUrlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8)
				.replace("%2D", "-")
				.replace("%5F", "_")
				.replace("%2E", ".")
				.replace("%21", "!")
				.replace("%2A", "*")
				.replace("%28", "(")
				.replace("%29", ")");
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
