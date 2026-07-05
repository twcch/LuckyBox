package com.luckybox.admin.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;
import com.luckybox.fairness.Fairness;

@Service
class AdminCampaignService {

	private static final Set<String> SOURCE_TYPES = Set.of("OFFICIAL", "SELF_MADE", "MIXED", "BLIND_BOX", "CARD", "GK", "PREORDER");
	private static final Set<String> STATUSES = Set.of("DRAFT", "SCHEDULED", "LIVE", "SOLD_OUT", "PAUSED", "ENDED");
	private static final Set<String> FAIRNESS_MODES = Set.of("SERVER_RANDOM", "HASH_COMMIT_REVEAL");
	private static final Set<String> SENSITIVE_LOCK_STATUSES = Set.of("LIVE", "PAUSED", "SOLD_OUT", "ENDED");
	private static final Set<String> SORTS = Set.of(
			"latest",
			"updatedDesc",
			"titleAsc",
			"priceAsc",
			"priceDesc",
			"remainingAsc",
			"remainingDesc",
			"status");

	private final AdminCampaignRepository adminCampaignRepository;
	private final AuditLogHelper auditLogHelper;

	AdminCampaignService(AdminCampaignRepository adminCampaignRepository, AuditLogHelper auditLogHelper) {
		this.adminCampaignRepository = adminCampaignRepository;
		this.auditLogHelper = auditLogHelper;
	}

	java.util.List<AdminCampaignResponse> campaigns(String status, String keyword, String sort) {
		requireAdmin();
		return adminCampaignRepository.findCampaigns(
				normalizeOptionalStatus(status),
				normalizeKeyword(keyword),
				normalizeSort(sort));
	}

	@Transactional
	AdminCampaignResponse createCampaign(AdminCampaignRequest request) {
		SessionPrincipal admin = requireAdmin();
		ValidatedCampaign validated = validateRequest(request, null);
		if (adminCampaignRepository.findCampaignBySlug(validated.request().slug()) != null) {
			throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_SLUG_EXISTS", "賞池代碼已存在，請使用不同 slug。");
		}
		try {
			long campaignId = adminCampaignRepository.createCampaign(validated.request());
			auditLogHelper.recordActorAction(
					admin.user().id(),
					admin.user().role(),
					"ADMIN_CAMPAIGN_CREATED",
					"Campaign",
					String.valueOf(campaignId),
					"{\"slug\":\"" + validated.request().slug() + "\"}");
			return adminCampaignRepository.findCampaign(campaignId);
		} catch (DataAccessException exception) {
			throw campaignWriteException(exception);
		}
	}

	@Transactional(noRollbackFor = ApiException.class)
	AdminCampaignResponse updateCampaign(long campaignId, AdminCampaignRequest request) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignResponse current = adminCampaignRepository.findCampaign(campaignId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
		}
		ValidatedCampaign validated = validateRequest(request, current);
		AdminCampaignResponse slugOwner = adminCampaignRepository.findCampaignBySlug(validated.request().slug());
		if (slugOwner != null && slugOwner.id() != campaignId) {
			throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_SLUG_EXISTS", "賞池代碼已存在，請使用不同 slug。");
		}
		List<String> lockedFields = lockedSensitiveFieldChanges(current, validated.request());
		if (!lockedFields.isEmpty()) {
			auditLogHelper.recordActorAction(
					admin.user().id(),
					admin.user().role(),
					"ADMIN_CAMPAIGN_SENSITIVE_CHANGE_BLOCKED",
					"Campaign",
					String.valueOf(campaignId),
					"{\"slug\":\"" + escapeJson(current.slug()) + "\",\"fields\":" + fieldsJson(lockedFields) + "}");
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"CAMPAIGN_SENSITIVE_FIELDS_LOCKED",
					"已開抽、暫停、完抽或已有抽賞紀錄的賞池不可直接修改敏感欄位，請建立修正版本並保留稽核紀錄。",
					Map.of("fields", lockedFields));
		}
		try {
			if (!adminCampaignRepository.updateCampaign(campaignId, validated.request(), validated.remainingTickets())) {
				throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
			}
		} catch (DataAccessException exception) {
			throw campaignWriteException(exception);
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_CAMPAIGN_UPDATED",
				"Campaign",
				String.valueOf(campaignId),
				"{\"slug\":\"" + validated.request().slug() + "\",\"status\":\"" + validated.request().status() + "\"}");
		return adminCampaignRepository.findCampaign(campaignId);
	}

	@Transactional
	AdminCampaignResponse publishCampaign(long campaignId) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignResponse current = requireCampaign(campaignId);
		if ("LIVE".equals(current.status())) {
			return current;
		}
		if ("SOLD_OUT".equals(current.status()) || "ENDED".equals(current.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_NOT_PUBLISHABLE", "已完抽或已結束賞池不可發布。");
		}
		requirePublishCompliance(
				current.sourceType(),
				current.commercialUseConfirmed(),
				current.officialLicenseConfirmed(),
				current.ageRestricted(),
				current.minimumAge(),
				current.ageVerificationNote());
		int totalTickets = adminCampaignRepository.totalTicketCount(campaignId);
		int availableTickets = adminCampaignRepository.availableTicketCount(campaignId);
		if (totalTickets == 0 || availableTickets == 0) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"CAMPAIGN_TICKETS_REQUIRED",
					"發布前必須先建立獎項並生成可抽 ticket。",
					Map.of("totalTickets", totalTickets, "availableTickets", availableTickets));
		}
		updateStatusOrThrow(campaignId, "LIVE");
		if ("HASH_COMMIT_REVEAL".equals(current.fairnessMode())) {
			String serverSeed = Fairness.newServerSeed();
			adminCampaignRepository.commitSeedIfAbsent(campaignId, serverSeed, Fairness.sha256Hex(serverSeed));
		}
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_CAMPAIGN_PUBLISHED",
				"Campaign",
				String.valueOf(campaignId),
				"{\"from\":\"" + current.status() + "\",\"to\":\"LIVE\"}");
		return adminCampaignRepository.findCampaign(campaignId);
	}

	@Transactional
	AdminCampaignResponse pauseCampaign(long campaignId) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignResponse current = requireCampaign(campaignId);
		if ("PAUSED".equals(current.status())) {
			return current;
		}
		if (!"LIVE".equals(current.status()) && !"SCHEDULED".equals(current.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_NOT_ACTIVE", "只有開抽中或即將開抽的賞池可以暫停。");
		}
		updateStatusOrThrow(campaignId, "PAUSED");
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_CAMPAIGN_PAUSED",
				"Campaign",
				String.valueOf(campaignId),
				"{\"from\":\"" + current.status() + "\",\"to\":\"PAUSED\"}");
		return adminCampaignRepository.findCampaign(campaignId);
	}

	@Transactional
	AdminCampaignResponse createCorrectionVersion(long campaignId) {
		SessionPrincipal admin = requireAdmin();
		AdminCampaignResponse current = requireCampaign(campaignId);
		String correctionSlug = correctionSlug(current);
		if (adminCampaignRepository.findCampaignBySlug(correctionSlug) != null) {
			throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_SLUG_EXISTS", "修正版本代碼已存在，請稍後再試。");
		}
		if ("LIVE".equals(current.status()) || "SCHEDULED".equals(current.status())) {
			updateStatusOrThrow(campaignId, "PAUSED");
		}
		AdminCampaignRequest correction = new AdminCampaignRequest(
				correctionSlug,
				current.title() + " 修正版",
				current.subtitle(),
				current.description(),
				current.coverImageUrl(),
				current.bannerImageUrl(),
				current.sourceType(),
				current.commercialUseConfirmed(),
				current.officialLicenseConfirmed(),
				current.rightsNotice(),
				current.ageRestricted(),
				current.minimumAge(),
				current.ageVerificationNote(),
				current.ipName(),
				current.brandName(),
				current.pricePerDraw(),
				current.totalTickets(),
				"DRAFT",
				current.salesStartAt(),
				current.salesEndAt(),
				current.shippingNote(),
				current.returnPolicyNote(),
				current.hasLastPrize(),
				current.lastPrizeRule(),
				current.fairnessMode(),
				"admin-campaign-" + correctionSlug);
		long correctionId = adminCampaignRepository.createCampaign(correction);
		adminCampaignRepository.copyPrizes(campaignId, correctionId);
		auditLogHelper.recordActorAction(
				admin.user().id(),
				admin.user().role(),
				"ADMIN_CAMPAIGN_CORRECTION_VERSION_CREATED",
				"Campaign",
				String.valueOf(campaignId),
				"{\"fromSlug\":\"" + escapeJson(current.slug()) + "\",\"correctionId\":" + correctionId
						+ ",\"correctionSlug\":\"" + escapeJson(correctionSlug) + "\"}");
		return adminCampaignRepository.findCampaign(correctionId);
	}

	@Transactional(readOnly = true)
	AdminCampaignDryRunResponse dryRunCampaign(long campaignId) {
		requireAdmin();
		AdminCampaignResponse current = requireCampaign(campaignId);
		requirePublishCompliance(
				current.sourceType(),
				current.commercialUseConfirmed(),
				current.officialLicenseConfirmed(),
				current.ageRestricted(),
				current.minimumAge(),
				current.ageVerificationNote());
		int totalTickets = adminCampaignRepository.totalTicketCount(campaignId);
		int availableTickets = adminCampaignRepository.availableTicketCount(campaignId);
		if (totalTickets == 0 || availableTickets == 0) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"CAMPAIGN_DRY_RUN_TICKETS_REQUIRED",
					"測試抽籤前必須先生成可抽 ticket。",
					Map.of("totalTickets", totalTickets, "availableTickets", availableTickets));
		}
		int requestedQuantity = Math.min(5, availableTickets);
		return new AdminCampaignDryRunResponse(
				campaignId,
				requestedQuantity,
				availableTickets,
				totalTickets,
				adminCampaignRepository.dryRunTickets(campaignId, requestedQuantity));
	}

	private ValidatedCampaign validateRequest(AdminCampaignRequest request, AdminCampaignResponse current) {
		String slug = normalizeSlug(request.slug());
		String sourceType = normalizeEnum(request.sourceType(), SOURCE_TYPES, "INVALID_SOURCE_TYPE", "賞池來源類型不正確。");
		String status = normalizeEnum(request.status(), STATUSES, "INVALID_CAMPAIGN_STATUS", "賞池狀態不正確。");
		String fairnessMode = normalizeEnum(request.fairnessMode(), FAIRNESS_MODES, "INVALID_FAIRNESS_MODE", "公平性模式不正確。");
		boolean commercialUseConfirmed = request.commercialUseConfirmed() == null || Boolean.TRUE.equals(request.commercialUseConfirmed());
		boolean officialLicenseConfirmed = Boolean.TRUE.equals(request.officialLicenseConfirmed());
		boolean ageRestricted = Boolean.TRUE.equals(request.ageRestricted());
		Integer minimumAge = ageRestricted ? request.minimumAge() : null;
		String ageVerificationNote = ageRestricted ? AdminCampaignRepository.cleanOptional(request.ageVerificationNote()) : null;
		if (ageRestricted) {
			if (minimumAge == null || minimumAge < 1) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "AGE_RESTRICTION_MINIMUM_REQUIRED", "啟用年齡限制時必須設定最低年齡。");
			}
			if (ageVerificationNote == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "AGE_VERIFICATION_NOTE_REQUIRED", "啟用年齡限制時必須填寫驗證方式。");
			}
		}
		if ("LIVE".equals(status) || "SCHEDULED".equals(status)) {
			requirePublishCompliance(sourceType, commercialUseConfirmed, officialLicenseConfirmed,
					ageRestricted, minimumAge, ageVerificationNote);
		}
		if (request.hasLastPrize() && AdminCampaignRepository.cleanOptional(request.lastPrizeRule()) == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "LAST_PRIZE_RULE_REQUIRED", "啟用最後賞時必須填寫最後賞規則。");
		}
		int soldTickets = current == null ? 0 : current.soldTickets();
		if (request.totalTickets() < soldTickets) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"TOTAL_TICKETS_TOO_LOW",
					"總抽數不可小於已售出抽數。",
					Map.of("soldTickets", soldTickets));
		}
		int remainingTickets = request.totalTickets() - soldTickets;
		if ("SOLD_OUT".equals(status)) {
			remainingTickets = 0;
		} else if (remainingTickets == 0 && "LIVE".equals(status)) {
			status = "SOLD_OUT";
		}
		String seedHash = AdminCampaignRepository.cleanOptional(request.seedHash());
		if (seedHash == null) {
			seedHash = "admin-campaign-" + slug;
		}
		return new ValidatedCampaign(
				new AdminCampaignRequest(
						slug,
						request.title().trim(),
						request.subtitle(),
						request.description().trim(),
						request.coverImageUrl(),
						request.bannerImageUrl(),
						sourceType,
						commercialUseConfirmed,
						officialLicenseConfirmed,
						rightsNotice(request.rightsNotice(), sourceType, officialLicenseConfirmed),
						ageRestricted,
						minimumAge,
						ageVerificationNote,
						request.ipName(),
						request.brandName(),
						request.pricePerDraw(),
						request.totalTickets(),
						status,
						request.salesStartAt(),
						request.salesEndAt(),
						request.shippingNote().trim(),
						request.returnPolicyNote().trim(),
						request.hasLastPrize(),
						request.lastPrizeRule(),
						fairnessMode,
						seedHash),
				remainingTickets);
	}

	private static List<String> lockedSensitiveFieldChanges(AdminCampaignResponse current, AdminCampaignRequest request) {
		List<String> changedFields = new ArrayList<>();
		if (!sensitiveFieldsLocked(current)) {
			return changedFields;
		}
		addIfChanged(changedFields, "slug", current.slug(), request.slug());
		addIfChanged(changedFields, "sourceType", current.sourceType(), request.sourceType());
		addIfChanged(changedFields, "commercialUseConfirmed", current.commercialUseConfirmed(), request.commercialUseConfirmed());
		addIfChanged(changedFields, "officialLicenseConfirmed", current.officialLicenseConfirmed(), request.officialLicenseConfirmed());
		addIfChanged(changedFields, "ageRestricted", current.ageRestricted(), request.ageRestricted());
		addIfChanged(changedFields, "minimumAge", current.minimumAge(), request.minimumAge());
		addIfChanged(changedFields, "pricePerDraw", current.pricePerDraw(), request.pricePerDraw());
		addIfChanged(changedFields, "totalTickets", current.totalTickets(), request.totalTickets());
		addIfChanged(changedFields, "status", current.status(), request.status());
		addIfChanged(changedFields, "salesStartAt", current.salesStartAt(), request.salesStartAt());
		addIfChanged(changedFields, "hasLastPrize", current.hasLastPrize(), request.hasLastPrize());
		addIfChanged(changedFields, "lastPrizeRule", current.lastPrizeRule(), request.lastPrizeRule());
		addIfChanged(changedFields, "fairnessMode", current.fairnessMode(), request.fairnessMode());
		addIfChanged(changedFields, "seedHash", current.seedHash(), request.seedHash());
		return changedFields;
	}

	private static boolean sensitiveFieldsLocked(AdminCampaignResponse current) {
		return current.soldTickets() > 0 || SENSITIVE_LOCK_STATUSES.contains(current.status());
	}

	private static void addIfChanged(List<String> fields, String field, String current, String next) {
		if (!Objects.equals(cleanNullable(current), cleanNullable(next))) {
			fields.add(field);
		}
	}

	private static void addIfChanged(List<String> fields, String field, int current, int next) {
		if (current != next) {
			fields.add(field);
		}
	}

	private static void addIfChanged(List<String> fields, String field, boolean current, Boolean next) {
		if (next == null || current != next) {
			fields.add(field);
		}
	}

	private static void addIfChanged(List<String> fields, String field, Integer current, Integer next) {
		if (!Objects.equals(current, next)) {
			fields.add(field);
		}
	}

	private static String cleanNullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String fieldsJson(List<String> fields) {
		StringBuilder builder = new StringBuilder("[");
		for (int index = 0; index < fields.size(); index++) {
			if (index > 0) {
				builder.append(',');
			}
			builder.append('"').append(escapeJson(fields.get(index))).append('"');
		}
		return builder.append(']').toString();
	}

	private static String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	private static String normalizeOptionalStatus(String status) {
		if (status == null || status.isBlank()) {
			return null;
		}
		return normalizeEnum(status, STATUSES, "INVALID_CAMPAIGN_STATUS", "賞池狀態不正確。");
	}

	private static String normalizeKeyword(String keyword) {
		return keyword == null || keyword.isBlank() ? null : keyword.trim();
	}

	private static String normalizeSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return "latest";
		}
		String normalized = sort.trim();
		if (!SORTS.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CAMPAIGN_SORT", "賞池排序不正確。");
		}
		return normalized;
	}

	private static String normalizeEnum(String value, Set<String> allowedValues, String code, String message) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		String normalized = value.trim().toUpperCase();
		if (!allowedValues.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
		}
		return normalized;
	}

	private static String normalizeSlug(String slug) {
		if (slug == null || slug.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CAMPAIGN_SLUG", "賞池代碼不可空白。");
		}
		String normalized = slug.trim().toLowerCase();
		if (!normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CAMPAIGN_SLUG", "賞池代碼只能使用小寫英文、數字與連字號。");
		}
		return normalized;
	}

	private static void requirePublishCompliance(
			String sourceType,
			boolean commercialUseConfirmed,
			boolean officialLicenseConfirmed,
			boolean ageRestricted,
			Integer minimumAge,
			String ageVerificationNote) {
		if (!commercialUseConfirmed) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_COMMERCIAL_USE_NOT_CONFIRMED", "發布前必須確認商品圖與素材可商用。");
		}
		if ("OFFICIAL".equals(sourceType) && !officialLicenseConfirmed) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_OFFICIAL_LICENSE_NOT_CONFIRMED", "官方賞發布前必須確認授權或進貨佐證。");
		}
		if (ageRestricted && (minimumAge == null || minimumAge < 1 || AdminCampaignRepository.cleanOptional(ageVerificationNote) == null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_AGE_RESTRICTION_INCOMPLETE", "年齡限制賞池發布前必須填寫最低年齡與驗證方式。");
		}
	}

	private static String rightsNotice(String rightsNotice, String sourceType, boolean officialLicenseConfirmed) {
		String cleaned = AdminCampaignRepository.cleanOptional(rightsNotice);
		if (cleaned != null) {
			return cleaned;
		}
		if ("OFFICIAL".equals(sourceType)) {
			return officialLicenseConfirmed
					? "官方或授權商品素材已由營運確認，正式公開前仍須保留授權或進貨佐證。"
					: "此賞池尚未確認官方授權，不可發布為官方賞。";
		}
		if ("SELF_MADE".equals(sourceType)) {
			return "自製賞素材與商品說明由營運確認可商用。";
		}
		return "商品來源與圖片素材由營運確認可於平台展示。";
	}

	private static String correctionSlug(AdminCampaignResponse current) {
		String suffix = "-correction-" + current.id() + "-" + (System.currentTimeMillis() % 100_000);
		String base = current.slug();
		if (base.length() + suffix.length() > 80) {
			base = base.substring(0, Math.max(1, 80 - suffix.length()));
			base = base.replaceAll("-+$", "");
		}
		return base + suffix;
	}

	private AdminCampaignResponse requireCampaign(long campaignId) {
		AdminCampaignResponse current = adminCampaignRepository.findCampaign(campaignId);
		if (current == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
		}
		return current;
	}

	private void updateStatusOrThrow(long campaignId, String status) {
		if (!adminCampaignRepository.updateStatus(campaignId, status)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "找不到指定賞池。");
		}
	}

	private static ApiException campaignWriteException(DataAccessException exception) {
		if (exception.getMessage() != null && exception.getMessage().contains("kuji_campaigns.slug")) {
			return new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_SLUG_EXISTS", "賞池代碼已存在，請使用不同 slug。");
		}
		return new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_WRITE_FAILED", "賞池資料不符合資料庫限制。");
	}

	private SessionPrincipal requireAdmin() {
		return SecurityPrincipals.requireAdmin();
	}

	private record ValidatedCampaign(AdminCampaignRequest request, int remainingTickets) {
	}
}
