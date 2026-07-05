package com.luckybox.campaign;

import java.util.Set;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.common.ApiException;

@Service
class CampaignService {

	private final CampaignRepository campaignRepository;
	private static final Set<String> VISIBLE_STATUSES = Set.of("LIVE", "SCHEDULED", "SOLD_OUT");
	private static final Set<String> SOURCE_TYPES = Set.of("OFFICIAL", "SELF_MADE", "MIXED", "BLIND_BOX", "CARD", "GK", "PREORDER");
	private static final Set<String> SORTS = Set.of("default", "latest", "popular", "priceAsc", "priceDesc", "remainingAsc");

	CampaignService(CampaignRepository campaignRepository) {
		this.campaignRepository = campaignRepository;
	}

	@Transactional(readOnly = true)
	CampaignPage listVisibleCampaigns(CampaignQuery query) {
		return campaignRepository.findVisibleCampaigns(normalizeQuery(query));
	}

	@Transactional(readOnly = true)
	CampaignDetail getCampaign(String slug) {
		return campaignRepository.findBySlug(slug)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"CAMPAIGN_NOT_FOUND",
						"找不到指定賞池。",
						Map.of("slug", slug)));
	}

	private CampaignQuery normalizeQuery(CampaignQuery query) {
		String keyword = cleanKeyword(query.keyword());
		String sourceType = cleanEnum(query.sourceType(), SOURCE_TYPES);
		String status = cleanEnum(query.status(), VISIBLE_STATUSES);
		String sort = SORTS.contains(query.sort()) ? query.sort() : "default";
		int size = Math.clamp(query.size(), 1, 12);
		int page = Math.max(query.page(), 0);
		return new CampaignQuery(keyword, sourceType, status, sort, page, size);
	}

	private static String cleanKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return keyword.trim();
	}

	private static String cleanEnum(String value, Set<String> allowedValues) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toUpperCase();
		return allowedValues.contains(normalized) ? normalized : null;
	}
}
