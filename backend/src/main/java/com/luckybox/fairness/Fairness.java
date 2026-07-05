package com.luckybox.fairness;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Commit/reveal fairness primitives for HASH_COMMIT_REVEAL campaigns.
 *
 * <p>At publish a {@link #newServerSeed() server seed} is generated and committed publicly as
 * {@code seed_hash = }{@link #sha256Hex(String) SHA-256(seed)}. Each draw derives its ticket from
 * {@link #hmacSha256Hex(String, String) HMAC-SHA256(seed, nonce)} and stores that HMAC as its
 * {@code random_proof}. After sell-out the seed is revealed, so anyone can check
 * {@code SHA-256(revealed_seed) == seed_hash} and re-derive every draw's HMAC.
 */
public final class Fairness {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private Fairness() {
	}

	/** A fresh 256-bit secret seed, hex-encoded. */
	public static String newServerSeed() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return toHex(bytes);
	}

	public static String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return toHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	public static String hmacSha256Hex(String key, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("HMAC-SHA256 failed", ex);
		}
	}

	/** Deterministic, uniform-ish index in {@code [0, modulus)} derived from an HMAC hex string. */
	public static int selectionIndex(String hmacHex, int modulus) {
		if (modulus <= 0) {
			throw new IllegalArgumentException("modulus must be positive");
		}
		BigInteger value = new BigInteger(hmacHex, 16);
		return value.mod(BigInteger.valueOf(modulus)).intValue();
	}

	private static String toHex(byte[] bytes) {
		char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int v = bytes[i] & 0xFF;
			out[i * 2] = HEX[v >>> 4];
			out[i * 2 + 1] = HEX[v & 0x0F];
		}
		return new String(out);
	}
}
