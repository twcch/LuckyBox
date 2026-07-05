package com.luckybox.banner;

public record BannerSummary(
		long id,
		String title,
		String imageUrl,
		String href,
		String position) {
}
