package com.luckybox.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

	@Test
	void forwardedHeadersAreIgnoredUnlessExplicitlyTrusted() throws Exception {
		RateLimitInterceptor interceptor = interceptor(false);

		MockHttpServletRequest first = request("10.0.0.10", "198.51.100.10");
		MockHttpServletRequest second = request("10.0.0.10", "198.51.100.11");

		assertThat(interceptor.preHandle(first, new MockHttpServletResponse(), new Object())).isTrue();
		MockHttpServletResponse response = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(second, response, new Object())).isFalse();
		assertThat(response.getStatus()).isEqualTo(429);
	}

	@Test
	void trustedXForwardedForUsesFirstClientAddressAsBucketKey() throws Exception {
		RateLimitInterceptor interceptor = interceptor(true);

		assertThat(interceptor.preHandle(request("10.0.0.10", "198.51.100.10, 10.0.0.10"),
				new MockHttpServletResponse(), new Object())).isTrue();
		assertThat(interceptor.preHandle(request("10.0.0.10", "198.51.100.11, 10.0.0.10"),
				new MockHttpServletResponse(), new Object())).isTrue();
		MockHttpServletResponse response = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(request("10.0.0.10", "198.51.100.10, 10.0.0.10"),
				response, new Object())).isFalse();
		assertThat(response.getStatus()).isEqualTo(429);
	}

	@Test
	void trustedForwardedHeaderSupportsQuotedIpv6AndFallsBackToRemoteAddr() throws Exception {
		RateLimitInterceptor interceptor = interceptor(true);

		MockHttpServletRequest ipv6 = request("10.0.0.10", null);
		ipv6.addHeader("Forwarded", "for=\"[2001:db8::17]:4711\";proto=https");
		assertThat(interceptor.preHandle(ipv6, new MockHttpServletResponse(), new Object())).isTrue();

		MockHttpServletRequest sameIpv6 = request("10.0.0.11", null);
		sameIpv6.addHeader("Forwarded", "for=\"[2001:db8::17]:4711\";proto=https");
		MockHttpServletResponse ipv6Response = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(sameIpv6, ipv6Response, new Object())).isFalse();
		assertThat(ipv6Response.getStatus()).isEqualTo(429);

		assertThat(interceptor.preHandle(request("10.0.0.20", "unknown"), new MockHttpServletResponse(), new Object()))
				.isTrue();
		MockHttpServletResponse fallbackResponse = new MockHttpServletResponse();
		assertThat(interceptor.preHandle(request("10.0.0.20", "unknown"), fallbackResponse, new Object())).isFalse();
		assertThat(fallbackResponse.getStatus()).isEqualTo(429);
	}

	private static RateLimitInterceptor interceptor(boolean trustForwardedHeaders) {
		return new RateLimitInterceptor(
				new RateLimiter(),
				true,
				1,
				60,
				1,
				60,
				1,
				60,
				trustForwardedHeaders);
	}

	private static MockHttpServletRequest request(String remoteAddr, String xForwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setRemoteAddr(remoteAddr);
		if (xForwardedFor != null) {
			request.addHeader("X-Forwarded-For", xForwardedFor);
		}
		return request;
	}
}
