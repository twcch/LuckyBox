package com.luckybox.accountorder;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.common.ApiException;

@Service
class AccountOrderService {

	private final AccountOrderRepository accountOrderRepository;

	AccountOrderService(AccountOrderRepository accountOrderRepository) {
		this.accountOrderRepository = accountOrderRepository;
	}

	@Transactional(readOnly = true)
	AccountOrdersResponse orders() {
		long userId = currentUserId();
		return new AccountOrdersResponse(
				accountOrderRepository.drawOrders(userId),
				accountOrderRepository.paymentOrders(userId));
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
