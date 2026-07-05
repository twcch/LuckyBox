package com.luckybox.coupon;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/coupons")
class AccountCouponController {

	private final CouponService couponService;

	AccountCouponController(CouponService couponService) {
		this.couponService = couponService;
	}

	@GetMapping
	List<CouponSummary> coupons() {
		return couponService.availableCoupons();
	}

	@PostMapping("/{couponId}/redeem")
	CouponRedemptionResponse redeemCoupon(@PathVariable long couponId) {
		return couponService.redeemPointBonusCoupon(couponId);
	}
}
