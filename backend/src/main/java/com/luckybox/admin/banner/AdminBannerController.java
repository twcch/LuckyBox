package com.luckybox.admin.banner;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/banners")
class AdminBannerController {

	private final AdminBannerService adminBannerService;

	AdminBannerController(AdminBannerService adminBannerService) {
		this.adminBannerService = adminBannerService;
	}

	@GetMapping
	List<AdminBannerResponse> banners(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String position,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminBannerService.banners(status, position, keyword);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminBannerResponse createBanner(@Valid @RequestBody AdminBannerRequest request) {
		return adminBannerService.createBanner(request);
	}

	@PatchMapping("/{bannerId}")
	AdminBannerResponse updateBanner(@PathVariable long bannerId, @Valid @RequestBody AdminBannerRequest request) {
		return adminBannerService.updateBanner(bannerId, request);
	}
}
