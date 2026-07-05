package com.luckybox.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/analytics")
class VisitorAnalyticsController {

	private final VisitorAnalyticsService visitorAnalyticsService;

	VisitorAnalyticsController(VisitorAnalyticsService visitorAnalyticsService) {
		this.visitorAnalyticsService = visitorAnalyticsService;
	}

	@PostMapping("/visit")
	@ResponseStatus(HttpStatus.ACCEPTED)
	VisitorVisitResponse recordVisit(@Valid @RequestBody VisitorVisitRequest request) {
		return visitorAnalyticsService.recordVisit(request);
	}
}
