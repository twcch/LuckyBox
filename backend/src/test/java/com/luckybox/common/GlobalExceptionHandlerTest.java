package com.luckybox.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

	@Test
	void unexpectedErrorsReturnGenericMessageWithoutStackTraceDetails() {
		GlobalExceptionHandler handler = new GlobalExceptionHandler();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test-error");
		RuntimeException exception = new RuntimeException("database password leaked in exception");

		ResponseEntity<ApiError> response = handler.handleUnexpectedException(exception, request);

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody()).isNotNull();
		ApiError body = response.getBody();
		assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
		assertThat(body.message()).isEqualTo("系統暫時無法處理，請稍後再試。");
		assertThat(body.path()).isEqualTo("/api/test-error");
		assertThat(body.details()).isEmpty();

		String responseText = body.toString();
		assertThat(responseText)
				.doesNotContain("database password leaked")
				.doesNotContain("RuntimeException")
				.doesNotContain("java.lang")
				.doesNotContain("at ");
	}
}
