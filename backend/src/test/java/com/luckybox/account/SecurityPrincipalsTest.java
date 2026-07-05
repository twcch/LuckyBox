package com.luckybox.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.luckybox.common.ApiException;

class SecurityPrincipalsTest {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void requiresAuthenticatedSessionPrincipal() {
		SessionPrincipal principal = authenticate("USER");

		assertThat(SecurityPrincipals.requireAuthenticated()).isSameAs(principal);
	}

	@Test
	void rejectsMissingAuthenticatedPrincipal() {
		assertThatThrownBy(SecurityPrincipals::requireAuthenticated)
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
					assertThat(error.code()).isEqualTo("AUTH_REQUIRED");
				});
	}

	@Test
	void allowsAdminRolesOnly() {
		SessionPrincipal admin = authenticate("ADMIN");
		assertThat(SecurityPrincipals.requireAdmin()).isSameAs(admin);

		SessionPrincipal superAdmin = authenticate("SUPER_ADMIN");
		assertThat(SecurityPrincipals.requireAdmin()).isSameAs(superAdmin);

		authenticate("USER");
		assertThatThrownBy(SecurityPrincipals::requireAdmin)
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(error.code()).isEqualTo("ADMIN_REQUIRED");
				});
	}

	@Test
	void allowsSuperAdminOnly() {
		SessionPrincipal superAdmin = authenticate("SUPER_ADMIN");
		assertThat(SecurityPrincipals.requireSuperAdmin()).isSameAs(superAdmin);

		authenticate("ADMIN");
		assertThatThrownBy(SecurityPrincipals::requireSuperAdmin)
				.isInstanceOfSatisfying(ApiException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(error.code()).isEqualTo("SUPER_ADMIN_REQUIRED");
				});
	}

	private static SessionPrincipal authenticate(String role) {
		SessionPrincipal principal = new SessionPrincipal(new AuthUser(
				1L,
				role.toLowerCase() + "@example.com",
				role,
				null,
				role,
				"ACTIVE",
				"REGULAR",
				0,
				0));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
		return principal;
	}
}
