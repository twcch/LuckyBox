package com.luckybox.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

	@Test
	void sendsViaMailSenderWhenEnabled() {
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		EmailService service = new EmailService(Optional.of(mailSender), true, "no-reply@luckybox.local");

		service.send("user@example.com", "主旨", "內文");

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		SimpleMailMessage sent = captor.getValue();
		org.assertj.core.api.Assertions.assertThat(sent.getTo()).containsExactly("user@example.com");
		org.assertj.core.api.Assertions.assertThat(sent.getFrom()).isEqualTo("no-reply@luckybox.local");
		org.assertj.core.api.Assertions.assertThat(sent.getSubject()).isEqualTo("主旨");
		org.assertj.core.api.Assertions.assertThat(sent.getText()).isEqualTo("內文");
	}

	@Test
	void doesNotSendWhenDisabled() {
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		EmailService service = new EmailService(Optional.of(mailSender), false, "no-reply@luckybox.local");

		service.send("user@example.com", "主旨", "內文");

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	void noMailSenderBeanFallsBackToLogWithoutError() {
		EmailService service = new EmailService(Optional.empty(), true, "no-reply@luckybox.local");
		assertThatCode(() -> service.send("user@example.com", "主旨", "內文")).doesNotThrowAnyException();
	}

	@Test
	void swallowsSendFailureToProtectMainFlow() {
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		Mockito.doThrow(new org.springframework.mail.MailSendException("smtp down"))
				.when(mailSender).send(any(SimpleMailMessage.class));
		EmailService service = new EmailService(Optional.of(mailSender), true, "no-reply@luckybox.local");

		assertThatCode(() -> service.send("user@example.com", "主旨", "內文")).doesNotThrowAnyException();
	}

	@Test
	void blankRecipientIsIgnored() {
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		EmailService service = new EmailService(Optional.of(mailSender), true, "no-reply@luckybox.local");

		service.send("  ", "主旨", "內文");

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}
}
