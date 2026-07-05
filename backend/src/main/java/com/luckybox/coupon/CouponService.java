package com.luckybox.coupon;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;
import com.luckybox.vip.VipService;
import com.luckybox.vip.VipStatusResponse;
import com.luckybox.vip.VipTiers;

@Service
class CouponService {

	private final CouponRepository couponRepository;
	private final VipService vipService;

	CouponService(CouponRepository couponRepository, VipService vipService) {
		this.couponRepository = couponRepository;
		this.vipService = vipService;
	}

	List<CouponSummary> availableCoupons() {
		long userId = currentUserId();
		vipService.status();
		return couponRepository.findAvailableCoupons(userId);
	}

	@Transactional
	CouponRedemptionResponse redeemPointBonusCoupon(long couponId) {
		long userId = currentUserId();
		VipStatusResponse vipStatus = vipService.status();
		CouponRepository.CouponForRedeem coupon = couponRepository.findCoupon(couponId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "找不到指定優惠券。"));
		validatePointBonusCoupon(userId, coupon, vipStatus.tier());
		long walletId = couponRepository.ensureWallet(userId);
		if (!couponRepository.incrementCouponUsedCount(coupon.id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "此優惠券已達使用上限。");
		}
		String usedAt;
		try {
			usedAt = couponRepository.createPointBonusUsage(userId, coupon.id(), coupon.value());
		} catch (DataAccessException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "此優惠券已使用過。");
		}
		CouponRepository.WalletBalance wallet =
				couponRepository.addBonusPointsFromCoupon(userId, walletId, coupon.id(), coupon.code(), coupon.value());
		return new CouponRedemptionResponse(
				coupon.id(),
				coupon.code(),
				coupon.value(),
				wallet.bonusPointBalance(),
				wallet.totalAvailableBalance(),
				usedAt);
	}

	private void validatePointBonusCoupon(long userId, CouponRepository.CouponForRedeem coupon, String currentTier) {
		if (!"ACTIVE".equals(coupon.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_ACTIVE", "此優惠券目前不可使用。");
		}
		Instant now = Instant.now();
		if (coupon.startsAt() != null && Instant.parse(coupon.startsAt()).isAfter(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_STARTED", "此優惠券尚未開始。");
		}
		if (coupon.endsAt() != null && Instant.parse(coupon.endsAt()).isBefore(now)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "此優惠券已過期。");
		}
		if (!"POINT_BONUS".equals(coupon.type())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_REDEEMABLE", "此優惠券不可直接領取贈點。");
		}
		if (!VipTiers.isEligible(currentTier, coupon.vipTier())) {
			String tierLabel = CouponRepository.vipTierLabel(coupon.vipTier());
			throw new ApiException(HttpStatus.FORBIDDEN, "COUPON_VIP_REQUIRED",
					"此優惠券限 " + (tierLabel == null ? "指定 VIP" : tierLabel) + " 會員使用。");
		}
		if (coupon.value() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALUE", "贈點券面額不正確。");
		}
		if (coupon.usageLimit() != null && coupon.usedCount() >= coupon.usageLimit()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "此優惠券已達使用上限。");
		}
		if (couponRepository.hasUserUsedCoupon(userId, coupon.id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "此優惠券已使用過。");
		}
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
