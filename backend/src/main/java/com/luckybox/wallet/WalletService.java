package com.luckybox.wallet;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.luckybox.account.SessionPrincipal;
import com.luckybox.audit.AuditLogHelper;
import com.luckybox.common.ApiException;

@Service
class WalletService {

	private static final List<TopUpPlanResponse> TOP_UP_PLANS = List.of(
			new TopUpPlanResponse("starter", "入門包", 100, 100, 0),
			new TopUpPlanResponse("value", "收藏包", 500, 500, 50),
			new TopUpPlanResponse("collector", "豪華包", 1000, 1000, 150));

	private final WalletRepository walletRepository;
	private final AuditLogHelper auditLogHelper;
	private final int firstDepositBonus;
	private final int spendThreshold;
	private final int spendThresholdBonus;
	private final boolean mockPaymentEnabled;
	private final int bonusPointExpiryDays;
	private final String defaultPaymentProvider;
	private final boolean ecpayEnabled;
	private final boolean linePayEnabled;
	private final boolean jkoPayEnabled;

	WalletService(
			WalletRepository walletRepository,
			AuditLogHelper auditLogHelper,
			@Value("${luckybox.promo.first-deposit-bonus:0}") int firstDepositBonus,
			@Value("${luckybox.promo.spend-threshold:0}") int spendThreshold,
			@Value("${luckybox.promo.spend-threshold-bonus:0}") int spendThresholdBonus,
			@Value("${luckybox.payment.mock-enabled:true}") boolean mockPaymentEnabled,
			@Value("${luckybox.payment.provider:MOCK}") String defaultPaymentProvider,
			@Value("${luckybox.payment.ecpay.enabled:false}") boolean ecpayEnabled,
			@Value("${luckybox.payment.linepay.enabled:false}") boolean linePayEnabled,
			@Value("${luckybox.payment.jkopay.enabled:false}") boolean jkoPayEnabled,
			@Value("${luckybox.points.bonus-expiry-days:365}") int bonusPointExpiryDays) {
		this.walletRepository = walletRepository;
		this.auditLogHelper = auditLogHelper;
		this.firstDepositBonus = Math.max(0, firstDepositBonus);
		this.spendThreshold = Math.max(0, spendThreshold);
		this.spendThresholdBonus = Math.max(0, spendThresholdBonus);
		this.mockPaymentEnabled = mockPaymentEnabled;
		this.bonusPointExpiryDays = Math.max(0, bonusPointExpiryDays);
		this.defaultPaymentProvider = normalizeProvider(defaultPaymentProvider);
		this.ecpayEnabled = ecpayEnabled;
		this.linePayEnabled = linePayEnabled;
		this.jkoPayEnabled = jkoPayEnabled;
	}

	WalletOverviewResponse overview() {
		long userId = currentUserId();
		boolean eligible = firstDepositBonus > 0 && walletRepository.countPaidPaymentOrders(userId) == 0;
		return new WalletOverviewResponse(
				walletRepository.walletSummary(userId).withBonusPointExpiry(bonusPointExpiryDays),
				walletRepository.ledger(userId),
				TOP_UP_PLANS,
				new FirstDepositPromoResponse(firstDepositBonus, eligible),
				spendThresholdPromo(userId));
	}

	private SpendThresholdPromoResponse spendThresholdPromo(long userId) {
		boolean active = spendThreshold > 0 && spendThresholdBonus > 0;
		if (!active) {
			return new SpendThresholdPromoResponse(false, spendThreshold, spendThresholdBonus, 0, 0, false);
		}
		int totalSpend = walletRepository.totalDrawSpend(userId);
		boolean reached = totalSpend >= spendThreshold;
		int remaining = reached ? 0 : spendThreshold - totalSpend;
		return new SpendThresholdPromoResponse(true, spendThreshold, spendThresholdBonus, totalSpend, remaining, reached);
	}

	List<WalletLedgerResponse> ledger() {
		return walletRepository.ledger(currentUserId());
	}

	List<TopUpPlanResponse> topUpPlans() {
		return TOP_UP_PLANS;
	}

	@Transactional
	PaymentOrderResponse createPaymentOrder(CreatePaymentOrderRequest request) {
		long userId = currentUserId();
		TopUpPlanResponse plan = findPlan(request.planId());
		String provider = request.provider() == null || request.provider().isBlank()
				? defaultPaymentProvider
				: normalizeProvider(request.provider());
		validateProviderForNewOrder(provider);
		walletRepository.ensureWallet(userId);
		WalletRepository.PaymentOrderRow order = walletRepository.createPaymentOrder(userId, plan, provider);
		return order.toResponse();
	}

	@Transactional
	PaymentOrderResponse completePaymentOrder(long orderId) {
		long userId = currentUserId();
		if (!mockPaymentEnabled) {
			throw mockPaymentDisabled();
		}
		WalletRepository.PaymentOrderRow currentOrder = walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));

		if ("PAID".equals(currentOrder.status())) {
			return currentOrder.toResponse();
		}
		if (!"PENDING".equals(currentOrder.status())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ORDER_NOT_PAYABLE", "此付款訂單目前不可完成付款。");
		}

		boolean markedPaid = walletRepository.markPaymentOrderPaid(userId, orderId);
		WalletRepository.PaymentOrderRow paidOrder = walletRepository.findPaymentOrder(userId, orderId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		creditIfJustPaid(paidOrder, markedPaid, "MOCK_PAYMENT_COMPLETED");
		return paidOrder.toResponse();
	}

	@Transactional
	PaymentOrderResponse completePaymentOrderFromWebhook(String provider, String merchantTradeNo, String providerPayload) {
		WalletRepository.PaymentOrderRow currentOrder = walletRepository.findPaymentOrderByMerchantTradeNo(provider, merchantTradeNo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		walletRepository.updateProviderPayload(provider, merchantTradeNo, providerPayload);
		if ("PAID".equals(currentOrder.status())) {
			return currentOrder.toResponse();
		}
		if (!"PENDING".equals(currentOrder.status())) {
			return currentOrder.toResponse();
		}
		boolean markedPaid = walletRepository.markPaymentOrderPaid(provider, merchantTradeNo);
		WalletRepository.PaymentOrderRow paidOrder = walletRepository.findPaymentOrderByMerchantTradeNo(provider, merchantTradeNo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		creditIfJustPaid(paidOrder, markedPaid, "PAYMENT_WEBHOOK_COMPLETED");
		return paidOrder.toResponse();
	}

	@Transactional
	PaymentOrderResponse markPaymentOrderFromWebhook(String provider, String merchantTradeNo, String status, String providerPayload) {
		if (!"FAILED".equals(status) && !"CANCELED".equals(status)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_WEBHOOK_STATUS_UNSUPPORTED", "付款狀態不支援。");
		}
		WalletRepository.PaymentOrderRow currentOrder = walletRepository.findPaymentOrderByMerchantTradeNo(provider, merchantTradeNo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "找不到指定付款訂單。"));
		walletRepository.updateProviderPayload(provider, merchantTradeNo, providerPayload);
		if ("PENDING".equals(currentOrder.status())) {
			walletRepository.markPaymentOrderTerminal(provider, merchantTradeNo, status);
			auditLogHelper.recordSystemAction(
					"PAYMENT_WEBHOOK_" + status,
					"PaymentOrder",
					String.valueOf(currentOrder.id()),
					"{\"userId\":" + currentOrder.userId() + ",\"amount\":" + currentOrder.amount() + "}");
		}
		return walletRepository.findPaymentOrderByMerchantTradeNo(provider, merchantTradeNo)
				.orElse(currentOrder)
				.toResponse();
	}

	private void creditIfJustPaid(WalletRepository.PaymentOrderRow paidOrder, boolean markedPaid, String auditAction) {
		if (!markedPaid) {
			return;
		}
		long userId = paidOrder.userId();
		long walletId = walletRepository.ensureWallet(userId);
		walletRepository.addCashPoints(userId, walletId, paidOrder.id(), paidOrder.pointAmount());
		walletRepository.addBonusPoints(userId, walletId, paidOrder.id(), paidOrder.bonusPointAmount());
		// 首儲活動: this order was just marked PAID, so a paid-order count of 1 means it is the user's first.
		if (firstDepositBonus > 0 && walletRepository.countPaidPaymentOrders(userId) == 1) {
			walletRepository.addFirstDepositBonus(userId, walletId, paidOrder.id(), firstDepositBonus);
			auditLogHelper.recordSystemAction(
					"FIRST_DEPOSIT_BONUS_GRANTED",
					"PaymentOrder",
					String.valueOf(paidOrder.id()),
					"{\"userId\":" + userId + ",\"bonus\":" + firstDepositBonus + "}");
		}
		auditLogHelper.recordSystemAction(
				auditAction,
				"PaymentOrder",
				String.valueOf(paidOrder.id()),
				"{\"userId\":" + userId + ",\"amount\":" + paidOrder.amount() + "}");
	}

	private TopUpPlanResponse findPlan(String planId) {
		return TOP_UP_PLANS.stream()
				.filter(plan -> plan.id().equals(planId))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "TOP_UP_PLAN_NOT_FOUND", "找不到指定儲值方案。"));
	}

	private void validateProviderForNewOrder(String provider) {
		switch (provider) {
			case "MOCK" -> {
				if (!mockPaymentEnabled && !"MOCK".equals(defaultPaymentProvider)) {
					throw mockPaymentDisabled();
				}
			}
			case "ECPAY" -> {
				if (!ecpayEnabled) {
					throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
							"PAYMENT_ECPAY_NOT_CONFIGURED",
							"ECPay 金流尚未完成設定。");
				}
			}
			case "LINEPAY" -> {
				if (!linePayEnabled) {
					throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
							"PAYMENT_LINEPAY_NOT_CONFIGURED",
							"LINE Pay 金流尚未完成設定。");
				}
			}
			case "JKOPAY" -> {
				if (!jkoPayEnabled) {
					throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
							"PAYMENT_JKOPAY_NOT_CONFIGURED",
							"街口支付尚未完成設定。");
				}
			}
			default -> throw new ApiException(HttpStatus.BAD_REQUEST,
					"PAYMENT_PROVIDER_UNSUPPORTED",
					"不支援的付款供應商。");
		}
	}

	private static String normalizeProvider(String provider) {
		String normalized = provider == null ? "MOCK" : provider.trim().toUpperCase();
		return normalized.isBlank() ? "MOCK" : normalized;
	}

	private static ApiException mockPaymentDisabled() {
		return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_MOCK_DISABLED", "Mock 金流功能未啟用。");
	}

	private long currentUserId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof SessionPrincipal sessionPrincipal) {
			return sessionPrincipal.user().id();
		}
		throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "請先登入。");
	}
}
