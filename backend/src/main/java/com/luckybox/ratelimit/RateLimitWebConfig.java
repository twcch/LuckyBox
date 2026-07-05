package com.luckybox.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class RateLimitWebConfig implements WebMvcConfigurer {

	private final RateLimitInterceptor rateLimitInterceptor;

	RateLimitWebConfig(RateLimitInterceptor rateLimitInterceptor) {
		this.rateLimitInterceptor = rateLimitInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor)
				.addPathPatterns(
						"/api/auth/login",
						"/api/auth/register",
						"/api/auth/forgot-password",
						"/api/account/draw-orders",
						"/api/webhooks/payment/**");
	}
}
