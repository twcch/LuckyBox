package com.luckybox.account;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.luckybox.common.ApiException;

public final class SecurityPrincipals {

	private SecurityPrincipals() {
	}

	public static SessionPrincipal requireAuthenticated() {
		SessionPrincipal principal = currentSessionPrincipal();
		if (principal != null) {
			return principal;
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "需要登入。");
	}

	public static SessionPrincipal requireAdmin() {
		SessionPrincipal principal = currentSessionPrincipal();
		if (isAdmin(principal)) {
			return principal;
		}
		throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "需要管理員權限。");
	}

	public static SessionPrincipal requireSuperAdmin() {
		SessionPrincipal principal = currentSessionPrincipal();
		if (principal != null && "SUPER_ADMIN".equals(principal.user().role())) {
			return principal;
		}
		throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_REQUIRED", "需要超級管理員權限。");
	}

	public static boolean isAdmin(SessionPrincipal principal) {
		return principal != null
				&& ("SUPER_ADMIN".equals(principal.user().role()) || "ADMIN".equals(principal.user().role()));
	}

	private static SessionPrincipal currentSessionPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication == null ? null : authentication.getPrincipal();
		return principal instanceof SessionPrincipal sessionPrincipal ? sessionPrincipal : null;
	}
}
