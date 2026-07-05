package com.luckybox.admin.user;

import java.util.List;

/**
 * 後台會員詳情：預設遮罩個資；只有明確揭露完整個資時才回傳未遮罩 contact/address 並寫入 audit log。
 */
public record AdminMemberDetailResponse(
		long id,
		boolean piiRevealed,
		String email,
		String displayName,
		String phone,
		String role,
		String roleLabel,
		String status,
		String statusLabel,
		String vipLevel,
		int cashPointBalance,
		int bonusPointBalance,
		int lockedBalance,
		int availableBalance,
		int drawOrderCount,
		int completedDrawCount,
		int totalDrawSpend,
		int paidOrderCount,
		int paidAmount,
		int prizeCount,
		int shipmentCount,
		String createdAt,
		String lastLoginAt,
		List<Address> addresses,
		List<LedgerEntry> recentLedger,
		List<PrizeItem> recentPrizes,
		List<Note> notes) {

	public record Address(
			long id,
			String recipientName,
			String phone,
			String postalCode,
			String city,
			String district,
			String addressLine,
			boolean defaultAddress) {
	}

	public record LedgerEntry(
			long id,
			String type,
			String typeLabel,
			int amount,
			String pointKind,
			int balanceAfter,
			String reason,
			String createdAt) {
	}

	public record PrizeItem(
			long id,
			String campaignSlug,
			String campaignTitle,
			String prizeRank,
			String prizeName,
			String ticketSerialNumber,
			String status,
			String statusLabel,
			Long shipmentId,
			String acquiredAt) {
	}

	public record Note(
			long id,
			String content,
			String authorName,
			String createdAt) {
	}
}
