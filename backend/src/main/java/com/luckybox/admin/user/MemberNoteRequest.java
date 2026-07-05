package com.luckybox.admin.user;

import jakarta.validation.constraints.Size;

public record MemberNoteRequest(
		@Size(max = 500, message = "備註內容不可超過 500 字")
		String content) {
}
