package com.luckybox.account;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * RFC 6238 / RFC 4226 TOTP（HMAC-SHA1、6 碼、30 秒、±1 步容錯），與 Google Authenticator / Authy 相容，
 * 無外部依賴。金鑰以 base32 字串保存於 users.totp_secret。
 */
@Component
public class TotpService {

	private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
	private static final int SECRET_BYTES = 20;
	private static final int DIGITS = 6;
	private static final int PERIOD_SECONDS = 30;
	private static final int SKEW_STEPS = 1;
	private static final SecureRandom RANDOM = new SecureRandom();

	public String generateSecret() {
		byte[] bytes = new byte[SECRET_BYTES];
		RANDOM.nextBytes(bytes);
		return base32Encode(bytes);
	}

	public boolean verify(String base32Secret, String code) {
		if (base32Secret == null || base32Secret.isBlank() || code == null) {
			return false;
		}
		String normalized = code.trim();
		if (!normalized.matches("\\d{6}")) {
			return false;
		}
		byte[] key;
		try {
			key = base32Decode(base32Secret);
		} catch (RuntimeException ex) {
			return false;
		}
		long currentStep = Instant.now().getEpochSecond() / PERIOD_SECONDS;
		for (int offset = -SKEW_STEPS; offset <= SKEW_STEPS; offset++) {
			if (codeForStep(key, currentStep + offset).equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	/** 取得目前時間步對應的驗證碼（測試與必要時的伺服器端輔助用）。 */
	public String currentCode(String base32Secret) {
		byte[] key = base32Decode(base32Secret);
		long step = Instant.now().getEpochSecond() / PERIOD_SECONDS;
		return codeForStep(key, step);
	}

	public String otpauthUri(String issuer, String accountName, String base32Secret) {
		String label = urlEncode(issuer) + ":" + urlEncode(accountName);
		return "otpauth://totp/" + label
				+ "?secret=" + base32Secret
				+ "&issuer=" + urlEncode(issuer)
				+ "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
	}

	private static String codeForStep(byte[] key, long step) {
		byte[] data = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
		byte[] hash = hmacSha1(key, data);
		int offset = hash[hash.length - 1] & 0x0F;
		int binary = ((hash[offset] & 0x7f) << 24)
				| ((hash[offset + 1] & 0xff) << 16)
				| ((hash[offset + 2] & 0xff) << 8)
				| (hash[offset + 3] & 0xff);
		int otp = binary % 1_000_000;
		return String.format("%06d", otp);
	}

	private static byte[] hmacSha1(byte[] key, byte[] data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			return mac.doFinal(data);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to compute TOTP", ex);
		}
	}

	private static String urlEncode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String base32Encode(byte[] data) {
		StringBuilder result = new StringBuilder();
		int buffer = 0;
		int bitsLeft = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xff);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				int index = (buffer >> (bitsLeft - 5)) & 0x1f;
				bitsLeft -= 5;
				result.append(BASE32.charAt(index));
			}
		}
		if (bitsLeft > 0) {
			int index = (buffer << (5 - bitsLeft)) & 0x1f;
			result.append(BASE32.charAt(index));
		}
		return result.toString();
	}

	private static byte[] base32Decode(String encoded) {
		String normalized = encoded.trim().replace("=", "").replace(" ", "").toUpperCase();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Empty TOTP secret");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bitsLeft = 0;
		for (int i = 0; i < normalized.length(); i++) {
			int value = BASE32.indexOf(normalized.charAt(i));
			if (value < 0) {
				throw new IllegalArgumentException("Invalid base32 character");
			}
			buffer = (buffer << 5) | value;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				out.write((buffer >> (bitsLeft - 8)) & 0xff);
				bitsLeft -= 8;
			}
		}
		return out.toByteArray();
	}
}
