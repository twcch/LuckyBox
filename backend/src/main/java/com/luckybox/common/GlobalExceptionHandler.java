package com.luckybox.common;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
		return ResponseEntity.status(exception.status())
				.body(new ApiError(
						exception.code(),
						exception.getMessage(),
						exception.status().value(),
						request.getRequestURI(),
						Instant.now(),
						exception.details()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException exception, HttpServletRequest request) {
		HttpStatus status = HttpStatus.BAD_REQUEST;
		Map<String, Object> details = exception.getBindingResult().getFieldErrors().stream()
				.collect(java.util.stream.Collectors.toMap(
						error -> error.getField(),
						error -> error.getDefaultMessage() == null ? "格式不正確" : error.getDefaultMessage(),
						(first, ignored) -> first));
		return ResponseEntity.status(status)
				.body(new ApiError(
						"VALIDATION_FAILED",
						"輸入資料不完整或格式不正確。",
						status.value(),
						request.getRequestURI(),
						Instant.now(),
						details));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		log.error("Unhandled API error at {}", request.getRequestURI(), exception);
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status)
				.body(new ApiError(
						"INTERNAL_ERROR",
						"系統暫時無法處理，請稍後再試。",
						status.value(),
						request.getRequestURI(),
						Instant.now(),
						Map.of()));
	}
}
