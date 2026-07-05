package com.luckybox.account;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.analytics.VisitorAnalyticsService;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
class AccountService {

	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogHelper auditLogHelper;
	private final TotpService totpService;
	private final VisitorAnalyticsService visitorAnalyticsService;

	AccountService(
			AccountRepository accountRepository,
			PasswordEncoder passwordEncoder,
			AuditLogHelper auditLogHelper,
			TotpService totpService,
			VisitorAnalyticsService visitorAnalyticsService) {
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditLogHelper = auditLogHelper;
		this.totpService = totpService;
		this.visitorAnalyticsService = visitorAnalyticsService;
	}

	@Transactional
	AuthUser register(RegisterRequest request, HttpServletRequest httpRequest) {
		if (accountRepository.findUserByEmail(request.email()).isPresent()) {
			throw emailAlreadyRegistered();
		}
		try {
			long userId = accountRepository.createUser(request, passwordEncoder.encode(request.password()));
			accountRepository.createWallet(userId);
			visitorAnalyticsService.linkRegistration(request.visitorId(), userId);
			AuthUser user = accountRepository.findUserById(userId).orElseThrow(() -> new ApiException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"USER_CREATE_FAILED",
					"會員建立失敗，請稍後再試。"));
			auditLogHelper.recordSystemAction(
					"USER_REGISTERED",
					"User",
					String.valueOf(user.id()),
					"{\"email\":\"" + user.email() + "\"}");
			signIn(user, httpRequest);
			return user;
		} catch (DataAccessException exception) {
			if (exception.getMessage() != null && exception.getMessage().contains("users.email")) {
				throw emailAlreadyRegistered();
			}
			throw exception;
		}
	}

	@Transactional
	AuthUser login(LoginRequest request, HttpServletRequest httpRequest) {
		String passwordHash = accountRepository.findPasswordHashByEmail(request.email())
				.orElseThrow(() -> invalidCredentials());
		if (!passwordEncoder.matches(request.password(), passwordHash)) {
			throw invalidCredentials();
		}
		AuthUser user = accountRepository.findUserByEmail(request.email()).orElseThrow(this::invalidCredentials);
		if (!"ACTIVE".equals(user.status())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "USER_NOT_ACTIVE", "此帳號目前無法登入。");
		}
		verifyTwoFactor(user.id(), request.totpCode());
		accountRepository.updateLastLogin(user.id());
		AuthUser refreshedUser = accountRepository.findUserById(user.id()).orElse(user);
		signIn(refreshedUser, httpRequest);
		return refreshedUser;
	}

	private void verifyTwoFactor(long userId, String totpCode) {
		TotpState totp = accountRepository.findTotpState(userId).orElse(null);
		if (totp == null || !totp.enabled()) {
			return;
		}
		if (totpCode == null || totpCode.isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "TWO_FACTOR_REQUIRED", "請輸入二階段驗證碼。");
		}
		if (!totpService.verify(totp.secret(), totpCode)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "TWO_FACTOR_INVALID", "二階段驗證碼不正確。");
		}
	}

	AuthUser currentUser() {
		AuthUser sessionUser = currentPrincipal().user();
		return accountRepository.findUserById(sessionUser.id()).orElse(sessionUser);
	}

	@Transactional
	AuthUser updateProfile(ProfileRequest request, HttpServletRequest httpRequest) {
		AuthUser currentUser = currentUser();
		if (!accountRepository.updateProfile(currentUser.id(), request)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到會員資料。");
		}
		AuthUser updatedUser = accountRepository.findUserById(currentUser.id())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "找不到會員資料。"));
		auditLogHelper.recordSystemAction(
				"USER_PROFILE_UPDATED",
				"User",
				String.valueOf(updatedUser.id()),
				"{\"email\":\"" + updatedUser.email() + "\"}");
		signIn(updatedUser, httpRequest);
		return updatedUser;
	}

	void logout(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

	List<AddressResponse> addresses() {
		return accountRepository.findAddresses(currentUser().id());
	}

	@Transactional
	AddressResponse createAddress(AddressRequest request) {
		long userId = currentUser().id();
		boolean defaultAddress = request.defaultAddress() || accountRepository.countAddresses(userId) == 0;
		if (defaultAddress) {
			accountRepository.clearDefaultAddress(userId);
		}
		long addressId = accountRepository.createAddress(userId, request, defaultAddress);
		return accountRepository.findAddresses(userId).stream()
				.filter(address -> address.id() == addressId)
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ADDRESS_CREATE_FAILED", "地址建立失敗。"));
	}

	@Transactional
	AddressResponse updateAddress(long addressId, AddressRequest request) {
		long userId = currentUser().id();
		boolean defaultAddress = request.defaultAddress();
		if (defaultAddress) {
			accountRepository.clearDefaultAddress(userId);
		}
		if (!accountRepository.updateAddress(userId, addressId, request, defaultAddress)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "找不到指定地址。");
		}
		if (!accountRepository.hasDefaultAddress(userId)) {
			accountRepository.makeNewestAddressDefault(userId);
		}
		return accountRepository.findAddresses(userId).stream()
				.filter(address -> address.id() == addressId)
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "找不到指定地址。"));
	}

	@Transactional
	void deleteAddress(long addressId) {
		long userId = currentUser().id();
		boolean removed = accountRepository.deleteAddress(userId, addressId);
		if (!removed) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "找不到指定地址。");
		}
		if (accountRepository.countAddresses(userId) > 0 && !accountRepository.hasDefaultAddress(userId)) {
			accountRepository.makeNewestAddressDefault(userId);
		}
	}

	private void signIn(AuthUser user, HttpServletRequest request) {
		SessionPrincipal principal = new SessionPrincipal(user);
		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
	}

	private SessionPrincipal currentPrincipal() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal;
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email 或密碼不正確。");
	}

	private ApiException emailAlreadyRegistered() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "這個 Email 已經註冊。");
	}
}
