package com.luckybox.wallet;

/** First-deposit promo state for the wallet overview: the bonus on offer and whether this user still qualifies. */
public record FirstDepositPromoResponse(int bonusPoints, boolean eligible) {
}
