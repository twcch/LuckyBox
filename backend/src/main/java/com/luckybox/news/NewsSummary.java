package com.luckybox.news;

public record NewsSummary(
		long id,
		String title,
		String slug,
		String excerpt,
		String publishedAt) {
}
