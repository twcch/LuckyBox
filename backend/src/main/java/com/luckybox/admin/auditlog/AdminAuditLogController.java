package com.luckybox.admin.auditlog;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.luckybox.common.ApiException;

@RestController
@RequestMapping("/api/admin/audit-logs")
class AdminAuditLogController {

	private final AdminAuditLogService adminAuditLogService;

	AdminAuditLogController(AdminAuditLogService adminAuditLogService) {
		this.adminAuditLogService = adminAuditLogService;
	}

	@GetMapping
	List<AdminAuditLogResponse> auditLogs(
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String actorRole,
			@RequestParam(required = false, name = "q") String keyword,
			@RequestParam(required = false) Integer limit) {
		return adminAuditLogService.auditLogs(action, entityType, actorRole, keyword, limit);
	}

	@DeleteMapping("/{auditLogId}")
	void deleteAuditLog(@PathVariable long auditLogId) {
		throw new ApiException(
				HttpStatus.METHOD_NOT_ALLOWED,
				"AUDIT_LOG_IMMUTABLE",
				"審計紀錄不可由後台刪除。");
	}
}
