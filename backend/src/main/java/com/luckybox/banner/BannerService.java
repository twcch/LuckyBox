package com.luckybox.banner;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.common.ApiException;

@Service
class BannerService {

	private static final Set<String> POSITIONS = Set.of("HOME_HERO", "HOME_SECTION");

	private final BannerRepository bannerRepository;

	BannerService(BannerRepository bannerRepository) {
		this.bannerRepository = bannerRepository;
	}

	List<BannerSummary> banners(String position) {
		return bannerRepository.findActiveBanners(normalizeOptionalPosition(position));
	}

	private static String normalizeOptionalPosition(String position) {
		if (position == null || position.isBlank()) {
			return null;
		}
		String normalized = position.trim().toUpperCase();
		if (!POSITIONS.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BANNER_POSITION", "Banner 位置不正確。");
		}
		return normalized;
	}
}
