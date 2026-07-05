package com.luckybox.wish;

public record AdminWishResponse(
		long id,
		String content,
		String authorDisplayName,
		String authorEmail,
		String status,
		String moderatorNote,
		String moderatedAt,
		String createdAt) {
}
