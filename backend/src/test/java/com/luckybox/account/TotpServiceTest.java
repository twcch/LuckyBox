package com.luckybox.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

	private final TotpService totpService = new TotpService();

	@Test
	void generatedSecretIsUsableBase32() {
		String secret = totpService.generateSecret();
		assertThat(secret).isNotBlank().matches("[A-Z2-7]+");
	}

	@Test
	void currentCodeIsSixDigitsAndVerifies() {
		String secret = totpService.generateSecret();
		String code = totpService.currentCode(secret);
		assertThat(code).matches("\\d{6}");
		assertThat(totpService.verify(secret, code)).isTrue();
	}

	@Test
	void rejectsWrongOrMalformedCodes() {
		String secret = totpService.generateSecret();
		assertThat(totpService.verify(secret, "12345")).isFalse(); // 位數不足
		assertThat(totpService.verify(secret, "abcdef")).isFalse(); // 非數字
		assertThat(totpService.verify(secret, null)).isFalse();
		assertThat(totpService.verify(null, "123456")).isFalse();
	}

	@Test
	void otpauthUriContainsSecretAndIssuer() {
		String secret = totpService.generateSecret();
		String uri = totpService.otpauthUri("LuckyBox", "admin@example.com", secret);
		assertThat(uri).startsWith("otpauth://totp/")
				.contains("secret=" + secret)
				.contains("issuer=LuckyBox")
				.contains("algorithm=SHA1")
				.contains("digits=6")
				.contains("period=30");
	}
}
