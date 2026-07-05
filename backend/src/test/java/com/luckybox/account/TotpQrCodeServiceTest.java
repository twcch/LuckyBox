package com.luckybox.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class TotpQrCodeServiceTest {

	private final TotpQrCodeService service = new TotpQrCodeService();

	@Test
	void rendersOtpAuthUriAsPngDataUri() {
		String dataUri = service.dataUri("otpauth://totp/LuckyBox:admin@example.com?secret=ABCDEF234567&issuer=LuckyBox");

		assertThat(dataUri).startsWith("data:image/png;base64,");
		byte[] png = Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
		assertThat(png).hasSizeGreaterThan(500);
		assertThat(png).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
	}

	@Test
	void blankUriReturnsBlankDataUri() {
		assertThat(service.dataUri("  ")).isBlank();
	}
}
