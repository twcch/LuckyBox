package com.luckybox.admin.auditsummary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-summary")
class AuditSummaryController {

	private final AuditSummaryService auditSummaryService;

	AuditSummaryController(AuditSummaryService auditSummaryService) {
		this.auditSummaryService = auditSummaryService;
	}

	@GetMapping("/{slug}")
	AuditSummaryResponse summary(@PathVariable String slug) {
		return auditSummaryService.summary(slug);
	}
}
