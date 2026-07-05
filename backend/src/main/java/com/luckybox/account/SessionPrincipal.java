package com.luckybox.account;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record SessionPrincipal(AuthUser user) implements UserDetails, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return user.email();
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return "ACTIVE".equals(user.status());
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return "ACTIVE".equals(user.status());
	}
}
