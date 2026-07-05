package com.luckybox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.luckybox.audit.AuditLogHelper;
import com.luckybox.mail.EmailService;

class PasswordResetServiceTest {

	@Test
	void requestResetEmailsRawTokenLinkAndStoresOnlyTheHash() throws Exception {
		PasswordResetRepository repository = Mockito.mock(PasswordResetRepository.class);
		EmailService emailService = Mockito.mock(EmailService.class);
		when(repository.findActiveUserIdByEmail("user@example.com")).thenReturn(Optional.of(42L));

		// 帶尾斜線的 base-url，用以鎖定建構子的去尾斜線正規化。
		PasswordResetService service = new PasswordResetService(
				repository,
				Mockito.mock(PasswordEncoder.class),
				Mockito.mock(AuditLogHelper.class),
				emailService,
				"https://app.luckybox.test/");

		service.requestReset(new ForgotPasswordRequest("user@example.com"));

		ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
		verify(repository).createToken(eq(42L), hashCaptor.capture(), anyString());
		ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
		verify(emailService).send(eq("user@example.com"), anyString(), bodyCaptor.capture());

		String body = bodyCaptor.getValue();
		String prefix = "https://app.luckybox.test/reset-password?token=";
		assertThat(body).contains(prefix);
		assertThat(body).doesNotContain(".test//reset-password"); // 去尾斜線：無雙斜線

		String rawToken = extractToken(body, prefix);
		assertThat(rawToken).matches("[0-9a-f]{64}");
		// 信中為原始 token，DB 只存其 SHA-256 雜湊。
		assertThat(sha256Hex(rawToken)).isEqualTo(hashCaptor.getValue());
		assertThat(body).doesNotContain(hashCaptor.getValue());
	}

	@Test
	void requestResetDoesNotEmailOrTokenUnknownAccount() {
		PasswordResetRepository repository = Mockito.mock(PasswordResetRepository.class);
		EmailService emailService = Mockito.mock(EmailService.class);
		when(repository.findActiveUserIdByEmail(anyString())).thenReturn(Optional.empty());

		PasswordResetService service = new PasswordResetService(
				repository,
				Mockito.mock(PasswordEncoder.class),
				Mockito.mock(AuditLogHelper.class),
				emailService,
				"http://localhost:5173");

		service.requestReset(new ForgotPasswordRequest("ghost@example.com"));

		verify(emailService, never()).send(anyString(), anyString(), anyString());
		verify(repository, never()).createToken(anyLong(), anyString(), anyString());
	}

	private static String extractToken(String body, String prefix) {
		int start = body.indexOf(prefix) + prefix.length();
		int end = start;
		while (end < body.length() && isHex(body.charAt(end))) {
			end++;
		}
		return body.substring(start, end);
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
	}

	private static String sha256Hex(String input) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
	}
}
