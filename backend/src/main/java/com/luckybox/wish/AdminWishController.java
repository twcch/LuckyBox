package com.luckybox.wish;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/wishes")
class AdminWishController {

	private final WishService wishService;

	AdminWishController(WishService wishService) {
		this.wishService = wishService;
	}

	@GetMapping
	List<AdminWishResponse> wishes(@RequestParam(required = false) String status) {
		return wishService.adminWishes(status);
	}

	@PatchMapping("/{wishId}")
	AdminWishResponse moderate(@PathVariable long wishId, @Valid @RequestBody ModerateWishRequest request) {
		return wishService.moderateWish(wishId, request);
	}
}
