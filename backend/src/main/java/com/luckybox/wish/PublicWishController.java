package com.luckybox.wish;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishes")
class PublicWishController {

	private final WishService wishService;

	PublicWishController(WishService wishService) {
		this.wishService = wishService;
	}

	@GetMapping
	List<WishResponse> publicWishes() {
		return wishService.publicWishes();
	}
}
