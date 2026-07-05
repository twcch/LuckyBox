package com.luckybox.wish;

import jakarta.validation.constraints.Size;

public record CreateWishRequest(
		@Size(max = 200, message = "願望內容請控制在 200 字以內")
		String content) {
}
