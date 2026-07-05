package com.luckybox.admin.campaign;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.common.ApiException;

@Service
class AdminPrizeLibraryService {

	private static final Set<String> CAMPAIGN_STATUSES = Set.of("DRAFT", "SCHEDULED", "LIVE", "SOLD_OUT", "PAUSED", "ENDED");

	private final AdminPrizeLibraryRepository adminPrizeLibraryRepository;

	AdminPrizeLibraryService(AdminPrizeLibraryRepository adminPrizeLibraryRepository) {
		this.adminPrizeLibraryRepository = adminPrizeLibraryRepository;
	}

	List<AdminPrizeLibraryResponse> prizes(
			String campaignStatus,
			String rank,
			Boolean lastPrize,
			String keyword,
			int limit) {
		requireAdmin();
		String normalizedStatus = normalizeOptional(campaignStatus);
		if (normalizedStatus != null && !CAMPAIGN_STATUSES.contains(normalizedStatus)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CAMPAIGN_STATUS", "不支援的賞池狀態。");
		}
		return adminPrizeLibraryRepository.findPrizes(
				normalizedStatus,
				normalizeOptional(rank),
				lastPrize,
				normalizeOptional(keyword),
				Math.max(1, Math.min(limit, 500)));
	}

	private static String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}
}
