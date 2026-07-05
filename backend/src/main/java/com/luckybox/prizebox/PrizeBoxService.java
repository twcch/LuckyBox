package com.luckybox.prizebox;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.vip.VipService;
import com.luckybox.vip.VipTiers;

@Service
class PrizeBoxService {

	private static final int SHIPPING_FEE = 80;
	private static final Set<String> STATUSES = Set.of("IN_BOX", "SHIPMENT_REQUESTED", "SHIPPED", "DELIVERED");

	private final PrizeBoxRepository prizeBoxRepository;
	private final AuditLogHelper auditLogHelper;
	private final VipService vipService;

	PrizeBoxService(PrizeBoxRepository prizeBoxRepository, AuditLogHelper auditLogHelper, VipService vipService) {
		this.prizeBoxRepository = prizeBoxRepository;
		this.auditLogHelper = auditLogHelper;
		this.vipService = vipService;
	}

	PrizeBoxOverviewResponse prizes(String status, String campaignSlug) {
		long userId = currentUserId();
		String normalizedStatus = normalizeStatus(status);
		String normalizedCampaignSlug = normalizeCampaignSlug(campaignSlug);
		return new PrizeBoxOverviewResponse(
				prizeBoxRepository.findPrizes(userId, normalizedStatus, normalizedCampaignSlug),
				prizeBoxRepository.campaignOptions(userId),
				prizeBoxRepository.statusCounts(userId),
				normalizedStatus,
				normalizedCampaignSlug);
	}

	List<ShipmentResponse> shipments() {
		return prizeBoxRepository.findShipments(currentUserId());
	}

	ShipmentResponse shipment(long shipmentId) {
		return prizeBoxRepository.findShipment(currentUserId(), shipmentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "找不到指定出貨單。"));
	}

	@Transactional
	ShipmentResponse createShipment(CreateShipmentRequest request) {
		long userId = currentUserId();
		List<Long> prizeIds = normalizedPrizeIds(request.prizeIds());
		PrizeBoxRepository.AddressRow address = prizeBoxRepository.findAddress(userId, request.addressId());
		if (address == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "找不到指定地址。");
		}

		List<PrizeBoxItemResponse> shippablePrizes = prizeBoxRepository.findShippablePrizes(userId, prizeIds);
		if (shippablePrizes.size() != prizeIds.size()) {
			throw new ApiException(HttpStatus.CONFLICT, "PRIZE_NOT_SHIPPABLE", "部分戰利品不可出貨或已申請出貨。");
		}

		FreeShippingCoupon coupon = resolveFreeShippingCoupon(userId, request.couponId());
		int shippingFee = coupon.applied() ? 0 : SHIPPING_FEE;
		long shipmentId = prizeBoxRepository.createShipment(userId, address, shippablePrizes.size(), shippingFee);
		int updatedRows = prizeBoxRepository.attachPrizesToShipment(userId, prizeIds, shipmentId);
		if (updatedRows != prizeIds.size()) {
			throw new ApiException(HttpStatus.CONFLICT, "PRIZE_NOT_SHIPPABLE", "部分戰利品不可出貨或已申請出貨。");
		}
		recordFreeShippingUsage(userId, shipmentId, coupon);
		auditLogHelper.recordSystemAction(
				"SHIPMENT_REQUESTED",
				"Shipment",
				String.valueOf(shipmentId),
				"{\"userId\":" + userId + ",\"itemCount\":" + prizeIds.size() + ",\"shippingFee\":" + shippingFee
						+ ",\"couponCode\":" + quotedOrNull(coupon.code()) + "}");
		return prizeBoxRepository.findShipment(userId, shipmentId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "找不到指定出貨單。"));
	}

	private FreeShippingCoupon resolveFreeShippingCoupon(long userId, Long couponId) {
		if (couponId == null) {
			return FreeShippingCoupon.none();
		}
		PrizeBoxRepository.CouponForShipment coupon = prizeBoxRepository.findCoupon(couponId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "找不到指定優惠券。"));
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
		if (!"FREE_SHIPPING".equals(coupon.type())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_NOT_REDEEMABLE", "此優惠券不可用於出貨免運。");
		}
		if (!VipTiers.isEligible(vipService.status().tier(), coupon.vipTier())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "COUPON_VIP_REQUIRED", "此優惠券限 VIP 會員使用。");
		}
		if (coupon.usageLimit() != null && coupon.usedCount() >= coupon.usageLimit()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "此優惠券已達使用上限。");
		}
		if (prizeBoxRepository.hasUserUsedCoupon(userId, coupon.id())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "此優惠券已使用過。");
		}
		return new FreeShippingCoupon(coupon.id(), coupon.code(), SHIPPING_FEE);
	}

	private void recordFreeShippingUsage(long userId, long shipmentId, FreeShippingCoupon coupon) {
		if (!coupon.applied()) {
			return;
		}
		if (!prizeBoxRepository.incrementCouponUsedCount(coupon.couponId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "此優惠券已達使用上限。");
		}
		try {
			prizeBoxRepository.createFreeShippingUsage(
					userId,
					coupon.couponId(),
					shipmentId,
					coupon.discountAmount());
		} catch (DataAccessException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "此優惠券已使用過。");
		}
	}

	private static String quotedOrNull(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private static List<Long> normalizedPrizeIds(List<Long> prizeIds) {
		List<Long> normalized = prizeIds.stream().distinct().toList();
		if (normalized.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_PRIZES_SELECTED", "請選擇要出貨的戰利品。");
		}
		if (new HashSet<>(normalized).size() != prizeIds.size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_PRIZE_SELECTED", "戰利品不可重複選取。");
		}
		return normalized;
	}

	private static String normalizeStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		String normalized = status.trim().toUpperCase();
		return STATUSES.contains(normalized) ? normalized : null;
	}

	private static String normalizeCampaignSlug(String campaignSlug) {
		if (campaignSlug == null || campaignSlug.isBlank()) {
			return null;
		}
		return campaignSlug.trim();
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}

	private record FreeShippingCoupon(Long couponId, String code, int discountAmount) {

		private static FreeShippingCoupon none() {
			return new FreeShippingCoupon(null, null, 0);
		}

		private boolean applied() {
			return couponId != null;
		}
	}
}
