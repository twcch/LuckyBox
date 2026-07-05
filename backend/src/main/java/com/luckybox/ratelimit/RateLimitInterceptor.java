package com.luckybox.ratelimit;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.luckybox.account.SessionPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 對敏感端點套用節流：認證類端點（登入/註冊/忘記密碼）以來源 IP 計數，抽賞端點以登入使用者計數。
 * 超過時間窗內允許次數時，於進入 controller 前短路回 429 RATE_LIMITED。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final RateLimiter rateLimiter;
	private final boolean enabled;
	private final int authLimit;
	private final Duration authWindow;
	private final int drawLimit;
	private final Duration drawWindow;
	private final int webhookLimit;
	private final Duration webhookWindow;
	private final boolean trustForwardedHeaders;

	RateLimitInterceptor(
			RateLimiter rateLimiter,
			@Value("${luckybox.ratelimit.enabled:true}") boolean enabled,
			@Value("${luckybox.ratelimit.auth-limit:20}") int authLimit,
			@Value("${luckybox.ratelimit.auth-window-seconds:60}") int authWindowSeconds,
				@Value("${luckybox.ratelimit.draw-limit:30}") int drawLimit,
				@Value("${luckybox.ratelimit.draw-window-seconds:60}") int drawWindowSeconds,
				@Value("${luckybox.ratelimit.webhook-limit:60}") int webhookLimit,
				@Value("${luckybox.ratelimit.webhook-window-seconds:60}") int webhookWindowSeconds,
				@Value("${luckybox.ratelimit.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
		this.rateLimiter = rateLimiter;
		this.enabled = enabled;
		this.authLimit = authLimit;
		this.authWindow = Duration.ofSeconds(Math.max(1, authWindowSeconds));
		this.drawLimit = drawLimit;
		this.drawWindow = Duration.ofSeconds(Math.max(1, drawWindowSeconds));
		this.webhookLimit = webhookLimit;
		this.webhookWindow = Duration.ofSeconds(Math.max(1, webhookWindowSeconds));
		this.trustForwardedHeaders = trustForwardedHeaders;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		String path = request.getRequestURI();
		String key;
		int capacity;
		Duration window;
		if (path.startsWith("/api/webhooks/payment/")) {
				key = "webhook:payment:" + clientIp(request);
			capacity = webhookLimit;
			window = webhookWindow;
		}
		else {
			switch (path) {
				case "/api/auth/login" -> {
						key = "auth:login:" + clientIp(request);
					capacity = authLimit;
					window = authWindow;
				}
				case "/api/auth/register" -> {
						key = "auth:register:" + clientIp(request);
					capacity = authLimit;
					window = authWindow;
				}
				case "/api/auth/forgot-password" -> {
						key = "auth:forgot:" + clientIp(request);
					capacity = authLimit;
					window = authWindow;
				}
				case "/api/account/draw-orders" -> {
						key = "draw:" + drawSubject(request);
					capacity = drawLimit;
					window = drawWindow;
				}
				default -> {
					return true;
				}
			}
		}
		if (rateLimiter.tryAcquire(key, capacity, window)) {
			return true;
		}
		writeTooManyRequests(response, path, window);
		return false;
	}

	private String drawSubject(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
			return "user:" + principal.user().id();
		}
		return "ip:" + clientIp(request);
	}

	private String clientIp(HttpServletRequest request) {
		if (trustForwardedHeaders) {
			String forwardedFor = firstForwardedFor(request.getHeader("X-Forwarded-For"));
			if (forwardedFor != null) {
				return forwardedFor;
			}
			String forwarded = firstForwardedHeader(request.getHeader("Forwarded"));
			if (forwarded != null) {
				return forwarded;
			}
		}
		return normalizeClientAddress(request.getRemoteAddr());
	}

	private static String firstForwardedFor(String header) {
		if (header == null || header.isBlank()) {
			return null;
		}
		for (String part : header.split(",")) {
			String normalized = normalizeClientAddress(part);
			if (!"unknown".equals(normalized)) {
				return normalized;
			}
		}
		return null;
	}

	private static String firstForwardedHeader(String header) {
		if (header == null || header.isBlank()) {
			return null;
		}
		for (String entry : header.split(",")) {
			for (String segment : entry.split(";")) {
				String trimmed = segment.trim();
				if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
					String normalized = normalizeClientAddress(trimmed.substring(4));
					if (!"unknown".equals(normalized)) {
						return normalized;
					}
				}
			}
		}
		return null;
	}

	private static String normalizeClientAddress(String rawAddress) {
		if (rawAddress == null || rawAddress.isBlank()) {
			return "unknown";
		}
		String value = rawAddress.trim();
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1).trim();
		}
		if ("unknown".equalsIgnoreCase(value)) {
			return "unknown";
		}
		if (value.startsWith("[") && value.contains("]")) {
			return value.substring(1, value.indexOf(']'));
		}
		int colonIndex = value.indexOf(':');
		if (colonIndex > 0 && colonIndex == value.lastIndexOf(':') && value.contains(".")) {
			return value.substring(0, colonIndex);
		}
		return value.isBlank() ? "unknown" : value;
	}

	private static void writeTooManyRequests(HttpServletResponse response, String path, Duration window)
			throws IOException {
		response.setStatus(429);
		response.setContentType("application/json;charset=UTF-8");
		response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
		response.getWriter().write("""
				{"code":"RATE_LIMITED","message":"操作過於頻繁，請稍後再試。","status":429,"path":"%s","timestamp":"%s","details":{}}
				""".formatted(path, Instant.now()));
	}
}
