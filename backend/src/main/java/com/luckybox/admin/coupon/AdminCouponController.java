package com.luckybox.admin.coupon;

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
@RequestMapping("/api/admin/coupons")
class AdminCouponController {

	private final AdminCouponService adminCouponService;

	AdminCouponController(AdminCouponService adminCouponService) {
		this.adminCouponService = adminCouponService;
	}

	@GetMapping
	List<AdminCouponResponse> coupons(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminCouponService.coupons(status, type, keyword);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminCouponResponse createCoupon(@Valid @RequestBody AdminCouponRequest request) {
		return adminCouponService.createCoupon(request);
	}

	@PatchMapping("/{couponId}")
	AdminCouponResponse updateCoupon(@PathVariable long couponId, @Valid @RequestBody AdminCouponRequest request) {
		return adminCouponService.updateCoupon(couponId, request);
	}
}
