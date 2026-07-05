package com.luckybox.wallet;

import java.util.List;

public record WalletOverviewResponse(
		WalletSummaryResponse wallet,
		List<WalletLedgerResponse> ledger,
		List<TopUpPlanResponse> topUpPlans,
		FirstDepositPromoResponse firstDepositPromo,
		SpendThresholdPromoResponse spendThresholdPromo) {
}
