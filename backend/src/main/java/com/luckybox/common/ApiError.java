package com.luckybox.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(
		String code,
		String message,
		int status,
		String path,
		Instant timestamp,
		Map<String, Object> details) {
}
