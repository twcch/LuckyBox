package com.luckybox.wish;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account/wishes")
class MemberWishController {

	private final WishService wishService;

	MemberWishController(WishService wishService) {
		this.wishService = wishService;
	}

	@GetMapping
	List<WishResponse> myWishes() {
		return wishService.myWishes();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	WishResponse createWish(@Valid @RequestBody CreateWishRequest request) {
		return wishService.createWish(request);
	}
}
