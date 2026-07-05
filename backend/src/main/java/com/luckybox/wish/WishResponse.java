package com.luckybox.wish;

public record WishResponse(
		long id,
		String content,
		String authorName,
		String status,
		String createdAt) {
}
