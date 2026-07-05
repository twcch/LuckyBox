package com.luckybox.banner;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banners")
class PublicBannerController {

	private final BannerService bannerService;

	PublicBannerController(BannerService bannerService) {
		this.bannerService = bannerService;
	}

	@GetMapping
	List<BannerSummary> banners(@RequestParam(required = false) String position) {
		return bannerService.banners(position);
	}
}
