package com.luckybox.admin.banner;

public record AdminBannerResponse(
		long id,
		String title,
		String imageUrl,
		String href,
		String position,
		String positionLabel,
		String status,
		String statusLabel,
		String publishAt,
		String unpublishAt,
		String createdAt,
		String updatedAt) {
}
