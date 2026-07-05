package com.luckybox.admin.consistency;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/consistency")
class ConsistencyController {

	private final ConsistencyService consistencyService;

	ConsistencyController(ConsistencyService consistencyService) {
		this.consistencyService = consistencyService;
	}

	@GetMapping
	ConsistencyReportResponse report() {
		return consistencyService.report();
	}
}
