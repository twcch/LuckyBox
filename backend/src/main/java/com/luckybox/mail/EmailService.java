package com.luckybox.mail;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 純文字寄信抽象。當 {@code luckybox.mail.enabled=true} 且有設定 SMTP（spring.mail.host）時，
 * 透過 JavaMailSender 實際寄出；否則退回開發用的記錄模式（只寫 log，不需 SMTP 伺服器）。
 * 寄信失敗會被吞掉並記 warn，避免中斷主流程（例如密碼重設仍須回 202 不洩漏帳號是否存在）。
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final Optional<JavaMailSender> mailSender;
	private final boolean enabled;
	private final String from;

	EmailService(
			Optional<JavaMailSender> mailSender,
			@Value("${luckybox.mail.enabled:false}") boolean enabled,
			@Value("${luckybox.mail.from:no-reply@luckybox.local}") String from) {
		this.mailSender = mailSender;
		this.enabled = enabled;
		this.from = from;
	}

	public void send(String to, String subject, String body) {
		if (to == null || to.isBlank()) {
			return;
		}
		if (!enabled || mailSender.isEmpty()) {
			// 僅在 INFO 記錄中繼資料；內文可能含重設 token 等敏感連結，留待 DEBUG（正式環境預設關閉）才輸出。
			log.info("[MAIL DISABLED] to={} subject={}", maskEmail(to), subject);
			if (log.isDebugEnabled()) {
				log.debug("[MAIL DISABLED] body to={}:\n{}", maskEmail(to), body);
			}
			return;
		}
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(from);
			message.setTo(to);
			message.setSubject(subject);
			message.setText(body);
			mailSender.get().send(message);
			log.info("Email sent: to={} subject={}", maskEmail(to), subject);
		}
		catch (RuntimeException ex) {
			log.warn("Failed to send email to {} subject={}: {}", maskEmail(to), subject, ex.getMessage());
		}
	}

	private static String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 1) {
			return "***";
		}
		return email.substring(0, 2) + "***" + email.substring(at);
	}
}
