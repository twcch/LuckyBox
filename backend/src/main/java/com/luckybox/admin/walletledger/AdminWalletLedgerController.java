package com.luckybox.admin.walletledger;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wallet-ledger")
class AdminWalletLedgerController {

	private final AdminWalletLedgerService adminWalletLedgerService;

	AdminWalletLedgerController(AdminWalletLedgerService adminWalletLedgerService) {
		this.adminWalletLedgerService = adminWalletLedgerService;
	}

	@GetMapping
	List<AdminWalletLedgerResponse> walletLedger(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String pointKind,
			@RequestParam(required = false) String referenceType,
			@RequestParam(required = false, name = "q") String keyword) {
		return adminWalletLedgerService.walletLedger(type, pointKind, referenceType, keyword);
	}
}
