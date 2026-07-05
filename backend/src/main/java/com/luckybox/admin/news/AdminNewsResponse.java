package com.luckybox.admin.news;

public record AdminNewsResponse(
		long id,
		String title,
		String slug,
		String content,
		String status,
		String statusLabel,
		String publishedAt,
		String unpublishAt,
		String createdAt,
		String updatedAt) {
}
