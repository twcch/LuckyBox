package com.luckybox.analytics;

public record VisitorVisitResponse(
		String visitorId,
		int visitCount,
		boolean registered) {
}
