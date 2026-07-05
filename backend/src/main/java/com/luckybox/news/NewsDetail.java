package com.luckybox.news;

public record NewsDetail(
		long id,
		String title,
		String slug,
		String content,
		String publishedAt) {
}
