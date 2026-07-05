package com.luckybox.analytics;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.common.ApiException;

@Service
public class VisitorAnalyticsService {

	private static final int MAX_PATH_LENGTH = 240;

	private final VisitorAnalyticsRepository visitorAnalyticsRepository;

	VisitorAnalyticsService(VisitorAnalyticsRepository visitorAnalyticsRepository) {
		this.visitorAnalyticsRepository = visitorAnalyticsRepository;
	}

	@Transactional
	public VisitorVisitResponse recordVisit(VisitorVisitRequest request) {
		String visitorId = normalizeVisitorId(request.visitorId());
		String path = normalizePath(request.path());
		visitorAnalyticsRepository.upsertVisit(visitorId, path, Instant.now().toString());
		return visitorAnalyticsRepository.findVisit(visitorId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"VISIT_RECORD_FAILED",
						"訪客紀錄建立失敗。"));
	}

	@Transactional
	public void linkRegistration(String visitorId, long userId) {
		if (visitorId == null || visitorId.isBlank()) {
			return;
		}
		visitorAnalyticsRepository.linkRegistration(normalizeVisitorId(visitorId), userId, Instant.now().toString());
	}

	private static String normalizeVisitorId(String visitorId) {
		String normalized = visitorId == null ? "" : visitorId.trim();
		if (normalized.length() < 12 || normalized.length() > 80 || !normalized.matches("^[A-Za-z0-9._:-]+$")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISITOR_ID", "visitorId 格式不正確。");
		}
		return normalized;
	}

	private static String normalizePath(String path) {
		if (path == null || path.isBlank()) {
			return "/";
		}
		String normalized = path.trim();
		return normalized.length() <= MAX_PATH_LENGTH ? normalized : normalized.substring(0, MAX_PATH_LENGTH);
	}
}
