package com.luckybox.admin.approval;

public record AdminApprovalRequestResponse(
		long id,
		String type,
		String typeLabel,
		String status,
		String statusLabel,
		String entityType,
		String entityId,
		String reason,
		String payloadJson,
		long requestedBy,
		String requestedByDisplayName,
		Long reviewedBy,
		String reviewedByDisplayName,
		String resultEntityType,
		String resultEntityId,
		String createdAt,
		String reviewedAt,
		String updatedAt) {
}
