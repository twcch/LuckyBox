package com.luckybox.vip;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;

/**
 * 依會員累積抽賞消費（COMPLETED draw_orders 的 point_spent 總和）決定 VIP 等級。
 * 門檻可由設定調整；查詢時順帶把 users.vip_level 刷新為計算後等級。
 */
@Service
public class VipService {

	private final VipRepository vipRepository;
	private final List<Tier> tiers;

	VipService(
			VipRepository vipRepository,
			@Value("${luckybox.vip.silver-threshold:1000}") int silver,
			@Value("${luckybox.vip.gold-threshold:5000}") int gold,
			@Value("${luckybox.vip.platinum-threshold:20000}") int platinum) {
		this.vipRepository = vipRepository;
		// 門檻遞增排序，防止設定顛倒導致等級判斷錯亂。
		List<Tier> ordered = new ArrayList<>();
		ordered.add(new Tier("REGULAR", "一般會員", 0));
		ordered.add(new Tier("SILVER", "銀卡", Math.max(1, silver)));
		ordered.add(new Tier("GOLD", "金卡", Math.max(silver + 1, gold)));
		ordered.add(new Tier("PLATINUM", "白金卡", Math.max(gold + 1, platinum)));
		this.tiers = List.copyOf(ordered);
	}

	@Transactional
	public VipStatusResponse status() {
		long userId = currentUserId();
		int spend = vipRepository.totalDrawSpend(userId);

		Tier current = tiers.get(0);
		for (Tier tier : tiers) {
			if (spend >= tier.threshold()) {
				current = tier;
			}
		}
		Tier next = null;
		for (Tier tier : tiers) {
			if (tier.threshold() > current.threshold()) {
				next = tier;
				break;
			}
		}

		if (!current.name().equals(vipRepository.currentVipLevel(userId))) {
			vipRepository.updateVipLevel(userId, current.name());
		}

		int spendToNext = next == null ? 0 : Math.max(0, next.threshold() - spend);
		int progress;
		if (next == null) {
			progress = 100;
		}
		else {
			int span = next.threshold() - current.threshold();
			progress = span <= 0 ? 0 : (int) Math.round((spend - current.threshold()) * 100.0 / span);
			progress = Math.max(0, Math.min(100, progress));
		}
		return new VipStatusResponse(
				current.name(),
				current.label(),
				spend,
				next == null ? null : next.name(),
				next == null ? null : next.label(),
				next == null ? 0 : next.threshold(),
				spendToNext,
				progress);
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}

	private record Tier(String name, String label, int threshold) {
	}
}
