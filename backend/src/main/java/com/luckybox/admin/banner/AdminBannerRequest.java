package com.luckybox.admin.banner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminBannerRequest(
		@NotBlank(message = "Banner 標題不可空白")
		@Size(max = 120, message = "Banner 標題不可超過 120 字")
		String title,

		@NotBlank(message = "Banner 圖片網址不可空白")
		@Size(max = 500, message = "Banner 圖片網址不可超過 500 字")
		String imageUrl,

		@Size(max = 500, message = "Banner 連結不可超過 500 字")
		String href,

		@NotBlank(message = "Banner 位置不可空白")
		@Size(max = 40, message = "Banner 位置不可超過 40 字")
		String position,

		@NotBlank(message = "Banner 狀態不可空白")
		@Size(max = 40, message = "Banner 狀態不可超過 40 字")
		String status,

		@Size(max = 80, message = "上架時間不可超過 80 字")
		String publishAt,

		@Size(max = 80, message = "下架時間不可超過 80 字")
		String unpublishAt) {
}
