package com.luckybox.accountorder;

import java.util.List;

public record AccountOrdersResponse(
		List<AccountDrawOrderResponse> drawOrders,
		List<AccountPaymentOrderResponse> paymentOrders) {
}
