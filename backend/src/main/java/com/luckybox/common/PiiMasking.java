package com.luckybox.common;

/**
 * Shared PII masking for admin-facing responses, so every endpoint masks email the same way
 * (several admin repositories carry private copies of this logic; new call sites should prefer this).
 */
public final class PiiMasking {

	private PiiMasking() {
	}

	public static String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return maskText(email, 2, 0);
		}
		String local = email.substring(0, at);
		String domain = email.substring(at + 1);
		return maskText(local, 2, 0) + "@" + maskDomain(domain);
	}

	public static String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return "";
		}
		return maskText(phone, 0, Math.min(3, phone.length()));
	}

	public static String maskName(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		return maskText(name, 1, 0);
	}

	public static String maskAddressLine(String addressLine) {
		if (addressLine == null || addressLine.isBlank()) {
			return "";
		}
		return maskText(addressLine, Math.min(3, addressLine.length()), 0);
	}

	private static String maskDomain(String domain) {
		if (domain == null || domain.isBlank()) {
			return "***";
		}
		int dot = domain.indexOf('.');
		if (dot <= 0) {
			return maskText(domain, 1, 0);
		}
		return maskText(domain.substring(0, dot), 1, 0) + domain.substring(dot);
	}

	private static String maskText(String value, int visiblePrefix, int visibleSuffix) {
		if (value == null || value.isBlank()) {
			return "";
		}
		if (value.length() <= visiblePrefix + visibleSuffix) {
			return "*".repeat(value.length());
		}
		String prefix = value.substring(0, Math.min(visiblePrefix, value.length()));
		String suffix = visibleSuffix == 0 ? "" : value.substring(value.length() - visibleSuffix);
		return prefix + "***" + suffix;
	}
}
