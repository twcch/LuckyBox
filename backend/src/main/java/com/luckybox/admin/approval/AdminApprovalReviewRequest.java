package com.luckybox.admin.approval;

import jakarta.validation.constraints.Size;

public record AdminApprovalReviewRequest(
		@Size(max = 200, message = "審核原因不可超過 200 字")
		String reason) {
}
