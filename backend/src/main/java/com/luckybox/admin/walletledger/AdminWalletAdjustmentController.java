package com.luckybox.admin.walletledger;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/wallet-adjustments")
class AdminWalletAdjustmentController {

	private final AdminWalletLedgerService adminWalletLedgerService;

	AdminWalletAdjustmentController(AdminWalletLedgerService adminWalletLedgerService) {
		this.adminWalletLedgerService = adminWalletLedgerService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminWalletLedgerResponse adjust(@Valid @RequestBody WalletAdjustmentRequest request) {
		return adminWalletLedgerService.applyAdjustment(request);
	}
}
