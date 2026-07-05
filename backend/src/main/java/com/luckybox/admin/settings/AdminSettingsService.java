package com.luckybox.admin.settings;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.luckybox.account.SecurityPrincipals;
import com.luckybox.common.ApiException;

@Service
class AdminSettingsService {

	private final Environment environment;

	AdminSettingsService(Environment environment) {
		this.environment = environment;
	}

	AdminSettingsResponse settings() {
		requireAdmin();
		return new AdminSettingsResponse(List.of(
				section("runtime", "執行環境", "正式部署與單一部署包共用的基礎設定。",
						item("profiles", "Active profiles", activeProfiles(), "ink", "空白代表 default profile。"),
						item("appBaseUrl", "App base URL", property("luckybox.app.base-url", "-"), "ink", "Email 與付款 redirect 連結基準。"),
						item("uploadDir", "Upload directory", property("luckybox.upload.dir", "-"), "ink", "本機圖片上傳儲存位置。"),
						item("maxImageSize", "Image upload limit", bytes(property("luckybox.upload.max-image-size-bytes", "0")), "ink", "後台圖片上傳大小上限。")),
				section("security", "安全設定", "登入、防偽與節流相關開關。",
						boolItem("csrf", "CSRF protection", bool("luckybox.security.csrf-enabled", true), "dev/prod 預設開啟，測試 profile 可關閉。"),
						boolItem("rateLimit", "Rate limiting", bool("luckybox.ratelimit.enabled", true), "登入、抽賞與金流 webhook 節流。"),
						boolItem("trustedProxy", "Trusted forwarded headers", bool("luckybox.ratelimit.trust-forwarded-headers", false), "僅在可信任 reverse proxy/CDN 後方開啟。"),
						item("authLimit", "Auth bucket", property("luckybox.ratelimit.auth-limit", "-") + " / " + property("luckybox.ratelimit.auth-window-seconds", "-") + "s", "ink", "登入、註冊與忘記密碼共用 IP bucket。"),
						item("drawLimit", "Draw bucket", property("luckybox.ratelimit.draw-limit", "-") + " / " + property("luckybox.ratelimit.draw-window-seconds", "-") + "s", "ink", "登入會員抽賞 bucket。")),
				section("payment", "金流設定", "只顯示 provider、開關與 callback 狀態，不暴露密鑰。",
						item("provider", "Default provider", property("luckybox.payment.provider", "MOCK"), "ink", "建立付款訂單時的預設 provider。"),
						boolItem("mockEnabled", "Mock payment", bool("luckybox.payment.mock-enabled", true), "prod profile 預設關閉。"),
						boolItem("ecpayEnabled", "ECPay", bool("luckybox.payment.ecpay.enabled", false), "正式金流第一順位。"),
							boolItem("linePayEnabled", "LINE Pay", bool("luckybox.payment.linepay.enabled", false), "Redirect checkout adapter。"),
							boolItem("jkoPayEnabled", "JKo Pay", bool("luckybox.payment.jkopay.enabled", false), "Redirect checkout adapter。")),
				section("mail", "Email/SMTP", "密碼重設與出貨狀態 email 設定。",
						boolItem("mailEnabled", "Mail enabled", bool("luckybox.mail.enabled", false), "關閉時使用 log fallback。"),
						item("mailFrom", "From", property("luckybox.mail.from", "-"), "ink", "寄件者地址。"),
						item("smtpHost", "SMTP host", configured("spring.mail.host"), property("spring.mail.host", "").isBlank() ? "warning" : "teal", "不顯示帳號、密碼或 secret。")),
				section("promo", "會員與促銷", "點數、VIP 與互動活動設定。",
						item("firstDeposit", "First deposit bonus", property("luckybox.promo.first-deposit-bonus", "0") + " LP", "ink", "首儲紅利。"),
						item("spendThreshold", "Spend threshold bonus", property("luckybox.promo.spend-threshold-bonus", "0") + " LP / " + property("luckybox.promo.spend-threshold", "0") + " LP", "ink", "累積消費跨門檻紅利。"),
						item("dailyCheckIn", "Daily check-in bonus", property("luckybox.promo.daily-check-in-bonus", "0") + " LP", "ink", "每日簽到基本獎勵。"),
						item("dailyCheckInStreakBonuses", "Daily check-in streak bonuses", property("luckybox.promo.daily-check-in-streak-bonuses", "-"), "ink", "格式為 連續天數:加碼 LP，逗號分隔。"),
						item("bonusExpiry", "Bonus point expiry", property("luckybox.points.bonus-expiry-days", "365") + " 天", "ink", "前台錢包顯示的紅利點有效期。"),
						item("vipThresholds", "VIP thresholds", property("luckybox.vip.silver-threshold", "0") + " / "
								+ property("luckybox.vip.gold-threshold", "0") + " / "
								+ property("luckybox.vip.platinum-threshold", "0") + " LP", "ink", "銀卡 / 金卡 / 白金門檻。"))));
	}

	private AdminSettingsResponse.AdminSettingsSection section(
			String key,
			String label,
			String helper,
			AdminSettingsResponse.AdminSettingsItem... items) {
		return new AdminSettingsResponse.AdminSettingsSection(key, label, helper, List.of(items));
	}

	private AdminSettingsResponse.AdminSettingsItem item(
			String key,
			String label,
			String value,
			String tone,
			String helper) {
		return new AdminSettingsResponse.AdminSettingsItem(key, label, value, tone, helper);
	}

	private AdminSettingsResponse.AdminSettingsItem boolItem(String key, String label, boolean enabled, String helper) {
		return item(key, label, enabled ? "啟用" : "停用", enabled ? "teal" : "warning", helper);
	}

	private boolean bool(String key, boolean defaultValue) {
		return environment.getProperty(key, Boolean.class, defaultValue);
	}

	private String property(String key, String defaultValue) {
		return environment.getProperty(key, defaultValue);
	}

	private String configured(String key) {
		String value = property(key, "");
		return value.isBlank() ? "未設定" : "已設定";
	}

	private String activeProfiles() {
		String[] profiles = environment.getActiveProfiles();
		return profiles.length == 0 ? "default" : String.join(", ", Arrays.asList(profiles));
	}

	private static String bytes(String value) {
		try {
			long bytes = Long.parseLong(value);
			if (bytes <= 0) {
				return "-";
			}
			if (bytes >= 1024 * 1024) {
				return (bytes / 1024 / 1024) + " MB";
			}
			return (bytes / 1024) + " KB";
		} catch (NumberFormatException exception) {
			return value;
		}
	}

	private void requireAdmin() {
		SecurityPrincipals.requireAdmin();
	}
}
