package com.luckybox.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VisitorVisitRequest(
		@NotBlank(message = "缺少 visitorId")
		@Size(min = 12, max = 80, message = "visitorId 長度不正確")
		@Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "visitorId 格式不正確")
		String visitorId,

		@Size(max = 240, message = "path 過長")
		String path) {
}
