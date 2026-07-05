package com.luckybox.admin.news;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminNewsRequest(
		@NotBlank(message = "公告標題不可空白")
		@Size(max = 140, message = "公告標題不可超過 140 字")
		String title,

		@NotBlank(message = "公告代碼不可空白")
		@Size(max = 120, message = "公告代碼不可超過 120 字")
		String slug,

		@NotBlank(message = "公告內容不可空白")
		@Size(max = 10_000, message = "公告內容不可超過 10000 字")
		String content,

		@NotBlank(message = "公告狀態不可空白")
		@Size(max = 40, message = "公告狀態不可超過 40 字")
		String status,

		@Size(max = 80, message = "發布時間不可超過 80 字")
		String publishedAt,

		@Size(max = 80, message = "下架時間不可超過 80 字")
		String unpublishAt) {
}
