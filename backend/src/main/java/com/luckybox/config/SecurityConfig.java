package com.luckybox.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import com.luckybox.common.ApiError;

@Configuration
class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			@Value("${luckybox.security.csrf-enabled:true}") boolean csrfEnabled) throws Exception {
		if (csrfEnabled) {
			// SPA cookie 模式：token 存於 JS 可讀的 XSRF-TOKEN cookie，前端（axios 預設）以 X-XSRF-TOKEN header 回送。
			// 使用 plain handler（非 XOR）讓 header 值等同 cookie raw token；CsrfCookieFilter 確保 cookie 於每次回應寫出。
			// 取捨：放棄 BREACH XOR 隨機化以換取 SPA cookie 相容，為業界常見作法。webhook 等機器對機器端點預先豁免。
			http.csrf(csrf -> csrf
					.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
					.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
					.ignoringRequestMatchers("/api/webhooks/**"))
					.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
		}
		else {
			http.csrf(AbstractHttpConfigurer::disable);
		}

		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/api/health", "/actuator/health", "/actuator/health/**", "/api/campaigns/**", "/api/news/**", "/api/banners/**", "/api/leaderboard/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/wishes").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/analytics/visit").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/webhooks/payment/linepay/**").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/webhooks/payment/**").permitAll()
						.requestMatchers("/api/auth/me", "/api/auth/logout", "/api/account/**").authenticated()
						.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
						.anyRequest().authenticated())
				.exceptionHandling(exception -> exception
						.authenticationEntryPoint((request, response, authException) ->
								writeApiError(response, 401, "AUTH_REQUIRED", "請先登入。", request.getRequestURI()))
						.accessDeniedHandler((request, response, accessDeniedException) -> {
							if (accessDeniedException instanceof CsrfException) {
								writeApiError(response, 403, "CSRF_TOKEN_INVALID",
										"請重新整理頁面後再試一次。", request.getRequestURI());
							}
							else {
								writeApiError(response, 403, "ADMIN_REQUIRED", "需要管理員權限。", request.getRequestURI());
							}
						}))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);

		return http.build();
	}

	private static void writeApiError(HttpServletResponse response, int status, String code, String message, String path)
			throws IOException {
		response.setStatus(status);
		response.setContentType("application/json;charset=UTF-8");
		ApiError error = new ApiError(code, message, status, path, Instant.now(), Map.of());
		response.getWriter().write("""
				{"code":"%s","message":"%s","status":%d,"path":"%s","timestamp":"%s","details":{}}
				""".formatted(
				error.code(),
				error.message(),
				error.status(),
				error.path(),
				error.timestamp()));
	}

	/**
	 * 強制延遲載入的 CsrfToken 實際產生，使 CookieCsrfTokenRepository 於每次回應寫出 XSRF-TOKEN cookie，
	 * 確保 SPA 在第一次安全請求（GET）後即可讀到 token，並於變更請求帶上。
	 */
	private static final class CsrfCookieFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(
				HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}
	}
}
