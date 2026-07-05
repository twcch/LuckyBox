package com.luckybox.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.mail.EmailService;

@Service
class PasswordResetService {

	private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final PasswordResetRepository passwordResetRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogHelper auditLogHelper;
	private final EmailService emailService;
	private final String appBaseUrl;

	PasswordResetService(
			PasswordResetRepository passwordResetRepository,
			PasswordEncoder passwordEncoder,
			AuditLogHelper auditLogHelper,
			EmailService emailService,
			@Value("${luckybox.app.base-url:http://localhost:5173}") String appBaseUrl) {
		this.passwordResetRepository = passwordResetRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditLogHelper = auditLogHelper;
		this.emailService = emailService;
		this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
	}

	/** Always succeeds from the caller's perspective — never reveals whether the email is registered. */
	@Transactional
	void requestReset(ForgotPasswordRequest request) {
		String email = request.email().trim();
		passwordResetRepository.findActiveUserIdByEmail(email).ifPresent(userId -> {
			String token = newToken();
			String expiresAt = Instant.now().plus(TOKEN_TTL).toString();
			passwordResetRepository.createToken(userId, sha256Hex(token), expiresAt);
			// 透過 EmailService 寄出重設信（dev/未設定 SMTP 時退回 log 模式，不洩漏 token 給呼叫端）。
			String resetUrl = appBaseUrl + "/reset-password?token=" + token;
			emailService.send(email, "重設您的 LuckyBox 密碼", """
					您好，

					我們收到重設 LuckyBox 密碼的請求。請於 30 分鐘內點擊以下連結完成重設：
					%s

					若您並未提出此請求，請忽略本信，您的密碼不會被變更。

					LuckyBox 團隊""".formatted(resetUrl));
			auditLogHelper.recordSystemAction(
					"PASSWORD_RESET_REQUESTED",
					"User",
					String.valueOf(userId),
					"{\"email\":\"" + email + "\"}");
		});
	}

	@Transactional
	void resetPassword(ResetPasswordRequest request) {
		String tokenHash = sha256Hex(request.token().trim());
		PasswordResetRepository.ResetToken token = passwordResetRepository.findToken(tokenHash)
				.orElseThrow(this::invalidToken);
		if (token.usedAt() != null) {
			throw invalidToken();
		}
		if (Instant.parse(token.expiresAt()).isBefore(Instant.now())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_EXPIRED", "重設連結已過期，請重新申請。");
		}
		if (!passwordResetRepository.markTokenUsed(token.id())) {
			throw invalidToken();
		}
		passwordResetRepository.updatePassword(token.userId(), passwordEncoder.encode(request.password()));
		auditLogHelper.recordSystemAction("PASSWORD_RESET_COMPLETED", "User", String.valueOf(token.userId()), "{}");
	}

	private ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID", "重設連結無效或已使用。");
	}

	private static String newToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
