package com.luckybox.checkin;

public record CheckInResultResponse(
		boolean justCheckedIn,
		int awardedAmount,
		CheckInStatusResponse status) {
}
