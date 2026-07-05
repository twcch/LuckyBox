package com.luckybox.checkin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
class CheckInService {

	private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
	private static final int STREAK_LOOKBACK_DAYS = 60;

	private final CheckInRepository checkInRepository;
	private final AuditLogHelper auditLogHelper;
	private final int dailyCheckInBonus;
	private final NavigableMap<Integer, Integer> streakBonuses;

	CheckInService(
			CheckInRepository checkInRepository,
			AuditLogHelper auditLogHelper,
			@Value("${luckybox.promo.daily-check-in-bonus:0}") int dailyCheckInBonus,
			@Value("${luckybox.promo.daily-check-in-streak-bonuses:}") String streakBonuses) {
		this.checkInRepository = checkInRepository;
		this.auditLogHelper = auditLogHelper;
		this.dailyCheckInBonus = Math.max(0, dailyCheckInBonus);
		this.streakBonuses = parseStreakBonuses(streakBonuses);
	}

	@Transactional(readOnly = true)
	CheckInStatusResponse status() {
		long userId = currentUserId();
		return buildStatus(userId, today());
	}

	@Transactional
	CheckInResultResponse checkIn() {
		long userId = currentUserId();
		LocalDate today = today();
		long walletId = checkInRepository.ensureWallet(userId);
		CheckInStatusResponse before = buildStatus(userId, today);
		int rewardAmount = before.rewardAmount();

		Optional<Long> checkInId = checkInRepository.insertCheckIn(userId, today, rewardAmount);
		if (checkInId.isEmpty()) {
			// 今日已簽到：保持冪等，不重複發點。
			return new CheckInResultResponse(false, 0, buildStatus(userId, today));
		}

		if (rewardAmount > 0) {
			checkInRepository.addCheckInBonus(userId, walletId, checkInId.get(), rewardAmount);
			auditLogHelper.recordSystemAction(
					"DAILY_CHECK_IN_BONUS_GRANTED",
					"DailyCheckIn",
					String.valueOf(checkInId.get()),
					"{\"userId\":" + userId
							+ ",\"bonus\":" + rewardAmount
							+ ",\"baseBonus\":" + dailyCheckInBonus
							+ ",\"streakBonus\":" + before.streakBonusAmount()
							+ ",\"date\":\"" + today + "\"}");
		}
		return new CheckInResultResponse(true, rewardAmount, buildStatus(userId, today));
	}

	private CheckInStatusResponse buildStatus(long userId, LocalDate today) {
		boolean checkedInToday = checkInRepository.hasCheckedIn(userId, today);
		int total = checkInRepository.totalCheckIns(userId);
		List<LocalDate> recent = checkInRepository.recentCheckInDates(userId, STREAK_LOOKBACK_DAYS);
		int streak = currentStreak(recent, today);
		int rewardStreak = checkedInToday ? streak : streak + 1;
		int streakBonus = streakBonuses.getOrDefault(rewardStreak, 0);
		Integer nextStreakBonusAt = nextStreakBonusAt(checkedInToday ? streak + 1 : rewardStreak);
		int nextStreakBonusAmount = nextStreakBonusAt == null ? 0 : streakBonuses.get(nextStreakBonusAt);
		int daysUntilNextStreakBonus = nextStreakBonusAt == null
				? 0
				: Math.max(0, nextStreakBonusAt - rewardStreak);
		return new CheckInStatusResponse(
				checkedInToday,
				dailyCheckInBonus + streakBonus,
				dailyCheckInBonus,
				streakBonus,
				streak,
				total,
				today.toString(),
				nextStreakBonusAt,
				nextStreakBonusAmount,
				daysUntilNextStreakBonus);
	}

	private Integer nextStreakBonusAt(int fromStreak) {
		return streakBonuses.ceilingKey(Math.max(1, fromStreak));
	}

	/**
	 * 連續簽到天數：若今日已簽到，從今日往回數；否則從昨日往回數（簽到前仍顯示既有連續紀錄）。
	 */
	private int currentStreak(List<LocalDate> dates, LocalDate today) {
		if (dates.isEmpty()) {
			return 0;
		}
		Set<LocalDate> set = new HashSet<>(dates);
		LocalDate cursor = today;
		if (!set.contains(cursor)) {
			cursor = today.minusDays(1);
			if (!set.contains(cursor)) {
				return 0;
			}
		}
		int streak = 0;
		while (set.contains(cursor)) {
			streak++;
			cursor = cursor.minusDays(1);
		}
		return streak;
	}

	private LocalDate today() {
		return LocalDate.now(TAIPEI);
	}

	private static NavigableMap<Integer, Integer> parseStreakBonuses(String value) {
		NavigableMap<Integer, Integer> bonuses = new TreeMap<>();
		if (value == null || value.isBlank()) {
			return bonuses;
		}
		for (String part : value.split(",")) {
			String[] pair = part.trim().split(":", 2);
			if (pair.length != 2) {
				continue;
			}
			try {
				int streak = Integer.parseInt(pair[0].trim());
				int amount = Integer.parseInt(pair[1].trim());
				if (streak > 0 && amount > 0) {
					bonuses.put(streak, amount);
				}
			} catch (NumberFormatException ignored) {
				// Ignore malformed entries so one bad ops value does not break check-in.
			}
		}
		return bonuses;
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
