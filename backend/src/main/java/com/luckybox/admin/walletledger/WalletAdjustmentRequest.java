package com.luckybox.admin.walletledger;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 人工點數調整輸入：對指定會員的現金點或贈點做正負調整，並要求填寫原因。 */
public record WalletAdjustmentRequest(
		@NotNull(message = "請指定要調整的會員")
		Long userId,

		@Size(max = 20, message = "點數種類不可超過 20 字")
		String pointKind,

		@NotNull(message = "請輸入調整點數")
		Integer amount,

		@Size(max = 200, message = "調整原因不可超過 200 字")
		String reason) {
}
