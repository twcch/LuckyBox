# API

## Current MVP Endpoints

### `GET /api/health`

Returns backend liveness metadata.

Example response:

```json
{
  "status": "UP",
  "service": "LuckyBox",
  "timestamp": "2026-06-11T00:00:00Z"
}
```

### `GET /api/campaigns`

Returns public campaigns that can be shown on the storefront. The current MVP response is backed by SQLite seed data and includes remaining ticket counts, pricing, status labels, source labels, age-restriction metadata, and a short rare-prize hint.
Public `remainingTickets`, `remainingRate`, `rareHint`, and popularity/remaining sorting are derived from currently `AVAILABLE` `kuji_tickets`, so stale campaign or prize counters do not change what shoppers see.

Supported query parameters:

- `q`: searches title, subtitle, description, and brand.
- `sourceType`: one of `OFFICIAL`, `SELF_MADE`, `MIXED`, `BLIND_BOX`, `CARD`, `GK`, or `PREORDER`.
- `status`: one of `LIVE`, `SCHEDULED`, or `SOLD_OUT`.
- `sort`: `default`, `latest`, `popular`, `priceAsc`, `priceDesc`, or `remainingAsc`.
- `page`: zero-based page number.
- `size`: page size, clamped to 1-12.

Example response:

```json
{
  "content": [
    {
      "id": 1,
      "slug": "star-collection-vol-1",
      "title": "星光收藏盒 Vol.1",
      "subtitle": "透明剩餘數的入門測試賞池",
      "sourceType": "MIXED",
      "sourceTypeLabel": "自營混套賞",
      "status": "LIVE",
      "statusLabel": "開抽中",
      "pricePerDraw": 100,
      "totalTickets": 80,
      "remainingTickets": 80,
      "hasLastPrize": true,
      "rareHint": "S賞剩 1",
      "remainingRate": 100.0,
      "ageRestricted": false,
      "minimumAge": null,
      "ageRestrictionLabel": "全年齡"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 3,
  "totalPages": 2,
  "sort": "default",
  "keyword": null,
  "sourceType": null,
  "status": null
}
```

### `GET /api/campaigns/{slug}`

Returns a public campaign detail with prize quantities, current probability, last-prize rules, source/rights notice, age-restriction disclosure, and shipping/return notes.
Public campaign and prize remaining counts are derived from `AVAILABLE` tickets; last-prize rows keep using the prize counter because they do not generate ordinary tickets.

Example response:

```json
{
  "slug": "star-collection-vol-1",
  "title": "星光收藏盒 Vol.1",
  "coverImageUrl": "https://images.example.com/star-cover.png",
  "bannerImageUrl": "https://images.example.com/star-banner.png",
  "pricePerDraw": 100,
  "totalTickets": 80,
  "remainingTickets": 80,
  "rightsNotice": "商品來源與圖片素材已由營運確認可於平台展示。",
  "ageRestricted": false,
  "minimumAge": null,
  "ageRestrictionLabel": "全年齡",
  "ageVerificationNote": null,
  "hasLastPrize": true,
  "lastPrizeRule": "最後一張普通 ticket 被抽出時，該筆抽賞額外獲得最後賞。",
  "prizes": [
    {
      "rank": "S",
      "name": "星光大賞收藏盒",
      "originalQuantity": 1,
      "remainingQuantity": 1,
      "lastPrize": false,
      "probability": 1.25
    }
  ]
}
```

Missing campaigns return the shared error format:

```json
{
  "code": "CAMPAIGN_NOT_FOUND",
  "message": "找不到指定賞池",
  "path": "/api/campaigns/missing"
}
```

### `GET /api/campaigns/{slug}/probabilities`

Compatibility endpoint for clients that request campaign odds separately from the full detail payload. It returns the same current ticket-backed `remainingTickets` and prize probability values used by `GET /api/campaigns/{slug}`.

Example response:

```json
{
  "slug": "star-collection-vol-1",
  "totalTickets": 80,
  "remainingTickets": 80,
  "prizes": [
    {
      "rank": "S",
      "name": "星光大賞收藏盒",
      "originalQuantity": 1,
      "remainingQuantity": 1,
      "lastPrize": false,
      "probability": 1.25
    }
  ]
}
```

### `GET /api/news`

Returns published announcements for the public news page. Draft and archived announcements are not included.

Example response:

```json
[
  {
    "id": 12,
    "title": "出貨批次調整公告",
    "slug": "shipping-update",
    "excerpt": "本週出貨批次將調整為週二與週五...",
    "publishedAt": "2026-06-18T10:00:00Z"
  }
]
```

### `GET /api/news/{slug}`

Returns a published announcement detail by slug. Missing, draft, or archived announcements return `NEWS_NOT_FOUND`.

Example response:

```json
{
  "id": 12,
  "title": "出貨批次調整公告",
  "slug": "shipping-update",
  "content": "本週出貨批次將調整為週二與週五。",
  "publishedAt": "2026-06-18T10:00:00Z"
}
```

### `GET /api/banners`

Returns active public banners. The homepage MVP requests `position=HOME_HERO` and uses the first active row; when no active banner is available, the frontend keeps its bundled static hero image.

Supported query parameters:

- `position`: `HOME_HERO` or `HOME_SECTION`.

Example response:

```json
[
  {
    "id": 7,
    "title": "夏季新品主視覺",
    "imageUrl": "https://images.example.com/summer-hero.png",
    "href": "/news",
    "position": "HOME_HERO"
  }
]
```

### `GET /api/leaderboard`

Returns the public draw feed and popular campaign ranking used by `/leaderboard` and the homepage live strip. Display names are masked before leaving the backend.

Supported query parameters:

- `liveLimit`: number of recent draw results, clamped to 1-20.
- `popularLimit`: number of popular campaigns, clamped to 1-20.
- `luckyLimit`: number of lucky members, clamped to 1-20.

`popularCampaigns` is served through a short in-memory cache to reduce repeated aggregate queries from the homepage and leaderboard page. The default TTL is 15 seconds and can be tuned with `LUCKYBOX_LEADERBOARD_POPULAR_CACHE_TTL_SECONDS`; `liveDraws` remains uncached for fresher public draw activity.

Example response:

```json
{
  "liveDraws": [
    {
      "drawResultId": 31,
      "drawOrderId": 18,
      "maskedDisplayName": "Lu**",
      "campaignSlug": "star-collection-vol-1",
      "campaignTitle": "星光收藏盒 Vol.1",
      "prizeRank": "A",
      "prizeName": "壓克力展示組",
      "resultIndex": 1,
      "createdAt": "2026-06-18T10:20:00Z"
    }
  ],
  "popularCampaigns": [
    {
      "campaignId": 1,
      "slug": "star-collection-vol-1",
      "title": "星光收藏盒 Vol.1",
      "status": "LIVE",
      "statusLabel": "開抽中",
      "pricePerDraw": 100,
      "totalTickets": 80,
      "remainingTickets": 64,
      "soldTickets": 16,
      "soldRate": 20.0,
      "drawCount": 16,
      "uniqueDrawers": 4,
      "rareHint": "S賞剩 1"
    }
  ],
  "luckyMembers": [
    {
      "position": 1,
      "displayName": "Lu**",
      "luckyWins": 3,
      "topRankWins": 1,
      "lastPrizeWins": 0
    }
  ],
  "generatedAt": "2026-06-18T10:20:10Z"
}
```

### `GET /api/account/coupons`

Returns currently available coupons for the authenticated member. Discount coupons can be submitted as `couponCode` when creating a draw order, and point-bonus coupons can be redeemed directly into the member's bonus-point wallet.

Only coupons with `ACTIVE` status, valid start/end time, remaining usage quota, no existing `APPLIED` usage by the current member, and a satisfied VIP requirement are returned. VIP-restricted coupons use `vipTier` (`SILVER`, `GOLD`, or `PLATINUM`) and `vipTierLabel`; null means available to all members.

Example response:

```json
[
  {
    "id": 9,
    "code": "SUMMER100",
    "type": "DISCOUNT",
    "typeLabel": "折扣券",
    "vipTier": "SILVER",
    "vipTierLabel": "銀卡以上",
    "value": 100,
    "minSpend": 500,
    "usageLimit": 100,
    "usedCount": 0,
    "startsAt": "2026-06-18T00:00:00Z",
    "endsAt": "2026-12-31T23:59:59Z"
  }
]
```

### `POST /api/account/coupons/{couponId}/redeem`

Redeems an authenticated member's point-bonus coupon into the bonus-point wallet. The MVP supports `POINT_BONUS` only here; discount coupons are applied during draw-order creation, and free-shipping coupons are applied during shipment checkout.

The coupon must be active, within its time window, below usage quota, unused by the same member, and allowed for the member's current VIP tier. A successful redemption increments `coupons.used_count`, writes `coupon_usages`, credits `wallets.bonus_point_balance`, and inserts a `COUPON_BONUS` wallet ledger row.

Example response:

```json
{
  "couponId": 9,
  "code": "WELCOME80",
  "pointAmount": 80,
  "bonusPointBalance": 130,
  "totalAvailableBalance": 630,
  "usedAt": "2026-06-18T12:00:00Z"
}
```

### `POST /api/auth/register`

Creates a user, hashes the password with BCrypt, creates the initial wallet, and starts a session.

Example request:

```json
{
  "email": "player@example.com",
  "password": "Password123!",
  "displayName": "Lucky 玩家",
  "phone": "0912345678"
}
```

### `POST /api/auth/login`

Authenticates an active user and starts a session.

Example request:

```json
{
  "email": "admin@luckybox.local",
  "password": "ChangeMe123!"
}
```

### `GET /api/auth/me`

Returns the current session user. Anonymous requests return `AUTH_REQUIRED`.

`GET /api/me` is kept as a compatibility alias and returns the same response.

### `POST /api/auth/logout`

Invalidates the current session and returns `204 No Content`.

### Admin two-factor authentication endpoints

Authenticated admin-only 2FA operations:

- `GET /api/admin/2fa`
- `POST /api/admin/2fa/setup`
- `POST /api/admin/2fa/enable`
- `POST /api/admin/2fa/disable`

`setup` returns the temporary TOTP secret, the `otpauth://` URI, and a PNG QR code data URI for the enable flow:

```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "otpauthUri": "otpauth://totp/LuckyBox:admin@example.com?secret=JBSWY3DPEHPK3PXP&issuer=LuckyBox",
  "qrCodeDataUri": "data:image/png;base64,iVBORw0KGgo..."
}
```

The setup secret and QR code should only be displayed during 2FA enrollment. `enable` and `disable` require the current 6-digit TOTP code and write admin audit records.

### Account profile endpoints

Authenticated profile read/update endpoints:

- `GET /api/account/profile`: used by `/account/profile` to refresh the current member profile.
- `PATCH /api/account/profile`: updates the current user's display name and phone number.
- `PUT /api/account/profile`: legacy-compatible alias for the same profile update payload.

Example request:

```json
{
  "displayName": "更新後玩家",
  "phone": "0922333444"
}
```

### `/api/account/addresses`

Authenticated address CRUD for shipment preparation:

- `GET /api/account/addresses`
- `POST /api/account/addresses`
- `PATCH /api/account/addresses/{addressId}`
- `PUT /api/account/addresses/{addressId}`
- `DELETE /api/account/addresses/{addressId}`

Compatibility aliases are also available at `GET /api/addresses`, `POST /api/addresses`, `PATCH /api/addresses/{addressId}`, `PUT /api/addresses/{addressId}`, and `DELETE /api/addresses/{addressId}`.

Example address payload:

```json
{
  "recipientName": "王小明",
  "phone": "0912345678",
  "postalCode": "100",
  "city": "台北市",
  "district": "中正區",
  "addressLine": "忠孝西路一段 1 號",
  "defaultAddress": true
}
```

### Account daily check-in endpoints

Authenticated member check-in operations:

- `GET /api/account/check-in`
- `POST /api/account/check-in`

The check-in date is based on `Asia/Taipei`. Each member can check in once per
day; duplicate POST requests are idempotent and do not grant points twice.
`rewardAmount` is the amount the next successful check-in would grant when
`checkedInToday=false`, or the amount already granted today when
`checkedInToday=true`.

Example status response before claiming a third consecutive day bonus:

```json
{
  "checkedInToday": false,
  "rewardAmount": 50,
  "baseRewardAmount": 20,
  "streakBonusAmount": 30,
  "currentStreak": 2,
  "totalCheckIns": 2,
  "today": "2026-07-05",
  "nextStreakBonusAt": 3,
  "nextStreakBonusAmount": 30,
  "daysUntilNextStreakBonus": 0
}
```

Example successful check-in response:

```json
{
  "justCheckedIn": true,
  "awardedAmount": 50,
  "status": {
    "checkedInToday": true,
    "rewardAmount": 50,
    "baseRewardAmount": 20,
    "streakBonusAmount": 30,
    "currentStreak": 3,
    "totalCheckIns": 3,
    "today": "2026-07-05",
    "nextStreakBonusAt": 7,
    "nextStreakBonusAmount": 80,
    "daysUntilNextStreakBonus": 4
  }
}
```

The base reward is configured with `luckybox.promo.daily-check-in-bonus`.
Streak bonuses are configured with
`luckybox.promo.daily-check-in-streak-bonuses`, using comma-separated
`streak:bonus` pairs such as `3:30,7:80,14:150,30:500`.

### `GET /api/account/wallet`

Returns the current user's wallet summary, bonus-point expiry policy, latest ledger rows, and MVP top-up plans. The endpoint also creates a missing wallet for older seeded users.

Example response:

```json
{
  "wallet": {
    "cashPointBalance": 500,
    "bonusPointBalance": 50,
    "lockedBalance": 0,
    "totalAvailableBalance": 550,
    "bonusPointExpiryDays": 365,
    "bonusPointExpiryLabel": "紅利點自入帳日起 365 天有效。"
  },
  "ledger": [
    {
      "type": "TOP_UP_BONUS",
      "amount": 50,
      "pointKind": "BONUS",
      "balanceAfter": 50,
      "referenceType": "PaymentOrder"
    }
  ],
  "topUpPlans": [
    {
      "id": "value",
      "label": "收藏包",
      "amount": 500,
      "pointAmount": 500,
      "bonusPointAmount": 50
    }
  ]
}
```

### Wallet and payment endpoints

Authenticated wallet MVP endpoints:

- `GET /api/account/wallet/ledger`
- `GET /api/account/wallet/top-up-plans`
- `POST /api/account/payment-orders`
- `POST /api/account/payment-orders/{orderId}/ecpay-checkout`
- `POST /api/account/payment-orders/{orderId}/linepay-checkout`
- `POST /api/account/payment-orders/{orderId}/jkopay-checkout`
- `POST /api/account/payment-orders/{orderId}/mock-checkout/confirm`
- `POST /api/account/payment-orders/{orderId}/complete`

Compatibility aliases are also available at `GET /api/wallet`, `GET /api/wallet/ledger`, `POST /api/payments/top-up`, and `POST /api/payments/mock/complete`. The mock completion alias accepts `{ "orderId": 123 }`.

Example payment order request:

```json
{
  "planId": "value",
  "provider": "ECPAY"
}
```

`provider` is optional. Local development defaults to `MOCK`; production defaults to `ECPAY` through `application-prod.properties`.

ECPay checkout endpoint response:

```json
{
  "orderId": 123,
  "provider": "ECPAY",
  "merchantTradeNo": "LBM0ABC123DEF",
  "actionUrl": "https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5",
  "method": "POST",
  "fields": {
    "MerchantID": "2000132",
    "MerchantTradeNo": "LBM0ABC123DEF",
    "MerchantTradeDate": "2026/07/04 22:00:00",
    "PaymentType": "aio",
    "TotalAmount": "500",
    "TradeDesc": "LuckyBox LP top up",
    "ItemName": "LuckyBox LP",
    "ReturnURL": "https://example.com/api/webhooks/payment/ecpay",
    "ChoosePayment": "Credit",
    "ClientBackURL": "https://example.com/account/orders",
    "CustomField1": "123",
    "CreditInstallment": "3,6",
    "EncryptType": "1",
    "CheckMacValue": "..."
  }
}
```

The frontend submits these fields as hidden inputs to ECPay's AioCheckOut endpoint. ECPay credentials remain server-side; only the fields required by ECPay checkout are returned to the browser. `CreditInstallment` is returned only when `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT` is configured; it must be one or more approved ECPay periods from `3,6,12,18,24`, or `30N`.

LINE Pay and JKo Pay checkout endpoints return redirect responses:

```json
{
  "orderId": 123,
  "provider": "LINEPAY",
  "merchantTradeNo": "LINEPAY-7-abc123def456",
  "redirectUrl": "https://sandbox-web-pay.line.me/web/payment/wait?...",
  "appRedirectUrl": "line://pay/payment/...",
  "qrImageUrl": null,
  "transactionId": "202607040001",
  "metadata": {
    "returnCode": "0000"
  }
}
```

For `JKOPAY`, `redirectUrl` is the JKo Pay `payment_url` and `qrImageUrl` may contain the provider QR image URL. The frontend redirects the browser to `redirectUrl`; provider credentials and signing secrets remain server-side.

The mock checkout confirm endpoint is used by the frontend sandbox route `/payment/mock/:orderId`. It validates the authenticated member owns the pending order, generates a server-side signed mock webhook, and credits points through the same webhook event pipeline used by provider callbacks. The card form is sandbox-only UI; card values are not sent to the backend or stored.

The legacy mock completion endpoint is idempotent: completing an already paid order returns the paid order without crediting points again.

### `POST /api/webhooks/payment/mock`

Receives signed mock payment webhook callbacks. The shared secret is configured by `luckybox.payment.mock-webhook-secret` or `LUCKYBOX_PAYMENT_MOCK_WEBHOOK_SECRET`. The development default is `dev-mock-webhook-secret`.

The request must include `X-LuckyBox-Signature`, an HMAC-SHA256 hex digest over:

```text
eventId|merchantTradeNo|amount|status
```

Supported statuses are `PAID`, `FAILED`, and `CANCELED`. `PAID` marks the matching pending payment order as paid, credits cash and bonus points through the same wallet ledger flow as the member mock completion endpoint, and writes a payment audit record. `FAILED` and `CANCELED` mark a pending order terminal without crediting points.

Example request:

```json
{
  "eventId": "evt_20260630_001",
  "merchantTradeNo": "MOCK-7-abc123def456",
  "amount": 500,
  "status": "PAID",
  "occurredAt": "2026-06-30T02:00:00Z"
}
```

Example response:

```json
{
  "provider": "MOCK",
  "eventId": "evt_20260630_001",
  "merchantTradeNo": "MOCK-7-abc123def456",
  "status": "PAID",
  "processed": true,
  "duplicate": false,
  "orderStatus": "PAID",
  "message": "OK"
}
```

Webhook events are persisted in `payment_webhook_events` with `UNIQUE(provider, event_id)`. Duplicate delivery returns `202 Accepted` with `duplicate=true` and does not credit points again. Amount mismatch is recorded with `processed=false`, returns `message=AMOUNT_MISMATCH`, and leaves the payment order unchanged. Invalid signatures return `401 WEBHOOK_SIGNATURE_INVALID` before recording the event. The endpoint is CSRF-exempt, public to the payment provider, and protected by the payment-webhook rate limit. Mock payment routes are controlled by `luckybox.payment.mock-enabled`; `application-prod.properties` disables them by default, returning `404 PAYMENT_MOCK_DISABLED` without recording webhook events or crediting wallets.

### `POST /api/webhooks/payment/ecpay`

Receives ECPay server-side payment result notifications as `application/x-www-form-urlencoded`, matching ECPay's `ReturnURL` callback.

Required production settings:

- `LUCKYBOX_PAYMENT_PROVIDER=ECPAY`
- `LUCKYBOX_PAYMENT_ECPAY_ENABLED=true`
- `LUCKYBOX_PAYMENT_ECPAY_MERCHANT_ID`
- `LUCKYBOX_PAYMENT_ECPAY_HASH_KEY`
- `LUCKYBOX_PAYMENT_ECPAY_HASH_IV`
- `LUCKYBOX_PAYMENT_ECPAY_ACTION_URL`
- `LUCKYBOX_PAYMENT_ECPAY_RETURN_URL`
- `LUCKYBOX_PAYMENT_ECPAY_CLIENT_BACK_URL`
- Optional for approved installment checkout: `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT`

Callback handling:

- Verifies `MerchantID`.
- Verifies `CheckMacValue` using ECPay's SHA256 checksum flow.
- Uses `TradeNo` as the webhook `event_id`.
- Maps `RtnCode=1` to `PAID`; other codes are recorded as `FAILED`.
- Rejects amount mismatch without crediting points.
- Ignores `SimulatePaid=1` by default unless `LUCKYBOX_PAYMENT_ECPAY_ACCEPT_SIMULATED=true`.
- Returns `1|OK` only after the callback has been safely accepted; invalid callbacks return `0|...`.

### LINE Pay Confirm And Cancel

- `GET /api/webhooks/payment/linepay/confirm/{merchantTradeNo}`
- `GET /api/webhooks/payment/linepay/cancel/{merchantTradeNo}`

LINE Pay redirects the customer back to the confirm URL with `transactionId`.
The backend calls LINE Pay confirm with the matching order amount and currency,
then records the transaction id in `payment_webhook_events`. Repeated confirm
redirects use the existing event and do not call LINE Pay again or credit points
twice. Cancel redirects mark a pending order as `CANCELED`.

Required production settings:

- `LUCKYBOX_PAYMENT_PROVIDER=LINEPAY`
- `LUCKYBOX_PAYMENT_LINEPAY_ENABLED=true`
- `LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_ID`
- `LUCKYBOX_PAYMENT_LINEPAY_CHANNEL_SECRET`
- `LUCKYBOX_PAYMENT_LINEPAY_API_BASE_URL`
- `LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true`

### JKo Pay Confirm And Result

- `POST /api/webhooks/payment/jkopay/confirm`
- `POST /api/webhooks/payment/jkopay/result`

The JKo Pay confirm URL accepts `{ "platform_order_id": "..." }` and returns
`{ "valid": true }` only when the matching LuckyBox `JKOPAY` order is still
pending. The result URL accepts JKo Pay's `transaction` payload, uses `tradeNo`
as the webhook event id, treats status `0` as paid, verifies the callback amount
matches the LuckyBox order, and handles duplicate delivery idempotently.

Required production settings:

- `LUCKYBOX_PAYMENT_PROVIDER=JKOPAY`
- `LUCKYBOX_PAYMENT_JKOPAY_ENABLED=true`
- `LUCKYBOX_PAYMENT_JKOPAY_API_KEY`
- `LUCKYBOX_PAYMENT_JKOPAY_SECRET_KEY`
- `LUCKYBOX_PAYMENT_JKOPAY_STORE_ID`
- `LUCKYBOX_PAYMENT_JKOPAY_ENTRY_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_CONFIRM_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_RESULT_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_RESULT_DISPLAY_URL`
- `LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true`

### `POST /api/account/draw-orders`

Creates an authenticated draw order. The backend reads campaign price, wallet balance, available tickets, and optional discount coupon state from SQLite; the frontend only sends the campaign slug, quantity, idempotency key, and optional coupon code. Single requests are limited to 1-10 draws through Bean Validation, and any client-submitted pricing or point-spend fields are ignored.

Example request:

```json
{
  "campaignSlug": "star-collection-vol-1",
  "quantity": 3,
  "idempotencyKey": "3f6dfb8f-4380-45b7-b8d0-7c9d1bb1ac88",
  "couponCode": "SUMMER100"
}
```

Example response:

```json
{
  "id": 12,
  "campaignSlug": "star-collection-vol-1",
  "campaignTitle": "星光收藏盒 Vol.1",
  "quantity": 3,
  "originalPointSpent": 300,
  "discountAmount": 100,
  "pointSpent": 200,
  "couponCode": "SUMMER100",
  "status": "COMPLETED",
  "balanceAfter": 800,
  "completedAt": "2026-06-18T11:30:00Z",
  "results": [
    {
      "ticketSerialNumber": "STAR_COLLECTION_VOL_1-0001",
      "prizeRank": "A",
      "prizeName": "壓克力展示組",
      "lastPrize": false
    }
  ]
}
```

Repeated requests with the same `idempotencyKey` return the existing draw order without spending points, reusing coupons, or drawing tickets again. Discount coupons must be active, within their time window, below usage quota, not previously used by the same member, and meet the order's original point spend threshold. The current MVP spends bonus points before cash points, writes negative `DRAW_SPEND` ledger rows, increments coupon usage, and records `coupon_usages` for successful discount applications.

### Draw order/result detail endpoints

Authenticated draw detail endpoints are scoped to the current member:

- `GET /api/account/draw-orders/{orderId}`: returns the same draw order response shape as a successful draw.
- `GET /api/draw-orders/{orderId}`: compatibility alias for the current member's draw order detail.
- `GET /api/draw-results/{resultId}`: compatibility endpoint for a single draw result.

Another member's order or result returns `404 DRAW_ORDER_NOT_FOUND` or `404 DRAW_RESULT_NOT_FOUND`.

### `GET /api/account/orders`

Returns the authenticated member's recent draw orders and mock payment orders for `/account/orders`. The response is scoped to the current session user and limited to the latest 50 rows per order type.

Example response:

```json
{
  "drawOrders": [
    {
      "id": 18,
      "campaignSlug": "star-collection-vol-1",
      "campaignTitle": "星光收藏盒 Vol.1",
      "quantity": 2,
      "originalPointSpent": 200,
      "discountAmount": 50,
      "pointSpent": 150,
      "couponCode": "SUMMER50",
      "status": "COMPLETED",
      "statusLabel": "完成",
      "resultCount": 2,
      "prizeSummary": "A賞 壓克力展示組、D賞 LuckyBox 貼紙包",
      "createdAt": "2026-06-18T12:00:00Z",
      "completedAt": "2026-06-18T12:00:02Z",
      "results": [
        {
          "id": 31,
          "ticketSerialNumber": "STAR_COLLECTION_VOL_1-0001",
          "prizeRank": "A",
          "prizeName": "壓克力展示組",
          "lastPrize": false
        }
      ]
    }
  ],
  "paymentOrders": [
    {
      "id": 9,
      "merchantTradeNo": "MOCK-1-123456789abc",
      "amount": 1000,
      "pointAmount": 1000,
      "bonusPointAmount": 100,
      "status": "PAID",
      "statusLabel": "已付款",
      "createdAt": "2026-06-18T11:55:00Z",
      "paidAt": "2026-06-18T11:55:03Z"
    }
  ]
}
```

### `GET /api/account/prizes`

Returns the authenticated user's prize box, including filter options and status counts.

Supported query parameters:

- `status`: `IN_BOX`, `SHIPMENT_REQUESTED`, `SHIPPED`, or `DELIVERED`.
- `campaignSlug`: filters prizes to one campaign.

Example response:

```json
{
  "items": [
    {
      "id": 42,
      "campaignSlug": "star-collection-vol-1",
      "campaignTitle": "星光收藏盒 Vol.1",
      "prizeRank": "A",
      "prizeName": "壓克力展示組",
      "ticketSerialNumber": "STAR_COLLECTION_VOL_1-0001",
      "status": "IN_BOX",
      "shipmentId": null,
      "acquiredAt": "2026-06-13T07:00:00Z"
    }
  ],
  "campaigns": [
    {
      "slug": "star-collection-vol-1",
      "title": "星光收藏盒 Vol.1",
      "itemCount": 1
    }
  ],
  "statusCounts": {
    "IN_BOX": 1
  },
  "status": null,
  "campaignSlug": null
}
```

### Shipment endpoints

Authenticated shipment request endpoints:

- `POST /api/account/shipments`
- `GET /api/account/shipments`: used by `/account/shipments` and the shipment history panel in `/account/prizes`.
- `GET /api/account/shipments/{shipmentId}`: used by `/account/shipments/:shipmentId` for single-shipment detail and tracking status.

Example shipment request:

```json
{
  "addressId": 7,
  "prizeIds": [42, 43],
  "couponId": 15
}
```

`couponId` is optional. When it points to an available `FREE_SHIPPING` coupon, the shipment fee is discounted to `0`, `coupons.used_count` is incremented, and a `coupon_usages` row is recorded with `reference_type = "Shipment"`.

Example shipment response:

```json
{
  "id": 9,
  "status": "REQUESTED",
  "itemCount": 2,
  "shippingFee": 80,
  "recipientName": "王小明",
  "phone": "0912345678",
  "postalCode": "100",
  "city": "台北市",
  "district": "中正區",
  "addressLine": "測試路 1 號",
  "carrier": "黑貓宅急便",
  "trackingNumber": "TA123456789",
  "requestedAt": "2026-06-13T07:05:00Z",
  "shippedAt": "2026-06-14T03:20:00Z",
  "deliveredAt": null,
  "items": [
    {
      "id": 42,
      "status": "SHIPMENT_REQUESTED",
      "shipmentId": 9
    }
  ]
}
```

The MVP only allows `IN_BOX` prizes without an existing shipment. Repeatedly requesting the same prize returns `PRIZE_NOT_SHIPPABLE`; using an expired, already-used, over-quota, or wrong-type coupon returns a coupon-specific validation error.

### Account notification endpoints

Authenticated account notification endpoints:

- `GET /api/account/notifications`
- `PATCH /api/account/notifications/{notificationId}/read`

Example notification overview:

```json
{
  "unreadCount": 1,
  "items": [
    {
      "id": 12,
      "type": "SHIPMENT_SHIPPED",
      "title": "出貨已交寄",
      "body": "出貨單 #9 已由 黑貓宅急便 交寄，追蹤碼 TA123456789。",
      "linkUrl": "/account/prizes",
      "referenceType": "Shipment",
      "referenceId": 9,
      "readAt": null,
      "createdAt": "2026-06-13T07:10:00Z"
    }
  ]
}
```

Shipment status notifications are created when an admin changes a shipment to `SHIPPED`, `DELIVERED`, `RETURNED`, or `EXCHANGED`. The same transition also sends a plain-text email through `EmailService` when SMTP is enabled; dev/test environments keep the existing log fallback and still create in-app notifications.

### Visitor analytics endpoint

Public visitor tracking operation used by the product metrics dashboard:

- `POST /api/analytics/visit`

Example visit request:

```json
{
  "visitorId": "web-550e8400-e29b-41d4-a716-446655440000",
  "path": "/campaigns/demo-kuji"
}
```

Example visit response:

```json
{
  "visitorId": "web-550e8400-e29b-41d4-a716-446655440000",
  "visitCount": 3,
  "registered": false
}
```

The frontend stores an anonymous `visitorId` in local storage, records visits on app load and route changes, and sends the same `visitorId` with registration so the dashboard can calculate visitor-to-registration conversion. The MVP stores only the anonymous visitor id, first/last path, visit count, timestamps, and optional linked user id after registration.

### Admin dashboard endpoint

Authenticated admin-only dashboard operation:

- `GET /api/admin/dashboard`

Example dashboard response:

```json
{
  "metrics": [
    {
      "key": "todayGmv",
      "label": "今日營收",
      "value": "NT$ 600",
      "helper": "已付款儲值訂單",
      "tone": "danger"
    },
    {
      "key": "todayDraws",
      "label": "今日抽數",
      "value": "42",
      "helper": "已完成 DrawOrder",
      "tone": "ink"
    },
    {
      "key": "todayActiveUsers",
      "label": "今日活躍會員",
      "value": "12",
      "helper": "今日有儲值或抽賞",
      "tone": "teal"
    },
    {
      "key": "nearSoldCampaigns",
      "label": "即將售完賞池",
      "value": "2",
      "helper": "剩餘 10% 或 10 張內",
      "tone": "warning"
    },
    {
      "key": "requestedShipments",
      "label": "未出貨",
      "value": "5",
      "helper": "等待後台處理",
      "tone": "warning"
    },
    {
      "key": "supportQueue",
      "label": "客服待處理",
      "value": "3",
      "helper": "願望審核與付款追蹤",
      "tone": "warning"
    },
    {
      "key": "drawAlerts",
      "label": "異常抽賞告警",
      "value": "1",
      "helper": "失敗抽賞與資料一致性",
      "tone": "danger"
    }
  ],
  "productMetrics": [
    {
      "key": "visitorToRegistration",
      "label": "訪客→註冊",
      "value": "12.5%",
      "helper": "已註冊訪客 / 匿名訪客",
      "tone": "teal"
    },
    {
      "key": "registrationToTopUp",
      "label": "註冊→首儲",
      "value": "18.2%",
      "helper": "付費會員 / 註冊會員",
      "tone": "teal"
    },
    {
      "key": "arppu",
      "label": "ARPPU",
      "value": "NT$ 820",
      "helper": "全部已付款 / 付費會員",
      "tone": "teal"
    },
    {
      "key": "drawApiP95Latency",
      "label": "抽賞 p95",
      "value": "120 ms",
      "helper": "完成抽賞 API 延遲",
      "tone": "ink"
    }
  ],
  "requestedShipments": [
    {
      "id": 9,
      "userDisplayName": "王小明",
      "userEmail": "user@example.com",
      "itemCount": 2,
      "requestedAt": "2026-06-13T07:05:00Z"
    }
  ],
  "recentActivities": [
    {
      "id": 1,
      "actorRole": "SYSTEM",
      "action": "DEV_SEED_CAMPAIGN",
      "entityType": "KujiCampaign",
      "entityId": "1",
      "createdAt": "2026-06-13T07:00:00Z"
    }
  ]
}
```

The MVP dashboard includes today's top-up amount, draw count, new users, live campaigns, requested shipments, failed payments, audit activity, product conversion metrics, visitor-to-registration conversion from anonymous visitor sessions, ARPPU, shipment request rate, refund/compensation rate, payment failure rate, draw API error rate, draw API p95 latency, recent requested shipments, and recent audit logs.

### Admin campaign endpoints

Authenticated admin-only campaign master-data operations:

- `GET /api/admin/campaigns`
- `POST /api/admin/campaigns`
- `PATCH /api/admin/campaigns/{campaignId}`
- `POST /api/admin/campaigns/{campaignId}/publish`
- `POST /api/admin/campaigns/{campaignId}/pause`
- `POST /api/admin/campaigns/{campaignId}/correction-version`
- `POST /api/admin/campaigns/{campaignId}/dry-run`

Supported list query parameters:

- `status`: `DRAFT`, `SCHEDULED`, `LIVE`, `SOLD_OUT`, `PAUSED`, or `ENDED`.
- `q`: keyword matched against slug, title, subtitle, or brand name.
- `sort`: `latest`, `updatedDesc`, `status`, `titleAsc`, `priceAsc`, `priceDesc`, `remainingAsc`, or `remainingDesc`.

Example create/update request:

```json
{
  "slug": "star-collection-vol-2",
  "title": "星光收藏盒 Vol.2",
  "subtitle": "新一季展示組",
  "description": "官方授權收藏盒賞池。",
  "coverImageUrl": "https://example.com/campaign-cover.png",
  "bannerImageUrl": "",
  "sourceType": "MIXED",
  "commercialUseConfirmed": true,
  "officialLicenseConfirmed": false,
  "rightsNotice": "商品來源與圖片素材由營運確認可於平台展示。",
  "ageRestricted": false,
  "minimumAge": null,
  "ageVerificationNote": "",
  "ipName": "LuckyBox",
  "brandName": "LuckyBox Originals",
  "pricePerDraw": 120,
  "totalTickets": 80,
  "status": "DRAFT",
  "salesStartAt": "2026-06-13T10:00:00",
  "salesEndAt": "",
  "shippingNote": "完成抽賞後可於戰利品盒申請出貨。",
  "returnPolicyNote": "抽賞商品依平台規則辦理退換貨。",
  "hasLastPrize": false,
  "lastPrizeRule": "",
  "fairnessMode": "SERVER_RANDOM",
  "seedHash": ""
}
```

Updating `totalTickets` preserves already-sold tickets and rejects totals below sold count. Publishing requires commercial-use confirmation, official-license confirmation when `sourceType=OFFICIAL`, complete age-restriction fields when enabled, and at least one generated `AVAILABLE` ticket before changing the campaign to `LIVE`; missing compliance returns `CAMPAIGN_COMMERCIAL_USE_NOT_CONFIRMED`, `CAMPAIGN_OFFICIAL_LICENSE_NOT_CONFIRMED`, or `CAMPAIGN_AGE_RESTRICTION_INCOMPLETE`. Pausing changes `LIVE` or `SCHEDULED` campaigns to `PAUSED`, which removes them from the public campaign list. `dry-run` returns a read-only sample of up to five currently available tickets with linked prize rank/name for publish-readiness checks; it does not draw, reserve, or void tickets. Non-admin users receive `ADMIN_REQUIRED`.

After a campaign has started, been paused, sold out, ended, or has any sold tickets, sensitive master-data fields are locked: `slug`, `sourceType`, `commercialUseConfirmed`, `officialLicenseConfirmed`, `ageRestricted`, `minimumAge`, `pricePerDraw`, `totalTickets`, `status`, `salesStartAt`, `hasLastPrize`, `lastPrizeRule`, `fairnessMode`, and `seedHash`. Attempts to change those fields return `400 CAMPAIGN_SENSITIVE_FIELDS_LOCKED` and write `ADMIN_CAMPAIGN_SENSITIVE_CHANGE_BLOCKED` to audit logs. Presentation fields such as title, subtitle, images, description, rights notice, age-verification note, brand/IP labels, sales end time, shipping note, and return-policy note remain editable and still write `ADMIN_CAMPAIGN_UPDATED`.

Use `POST /api/admin/campaigns/{campaignId}/correction-version` when locked fields must be corrected. The endpoint pauses the original campaign when it is `LIVE` or `SCHEDULED`, creates a new `DRAFT` campaign with a `-correction-...` slug, copies prize definitions with fresh remaining quantities, does not copy tickets or draw history, and records `ADMIN_CAMPAIGN_CORRECTION_VERSION_CREATED`.

Example dry-run response:

```json
{
  "campaignId": 12,
  "requestedQuantity": 3,
  "availableTickets": 3,
  "totalTickets": 3,
  "results": [
    {
      "serialNumber": "star-collection-vol-2-A-0001",
      "rank": "A",
      "prizeName": "壓克力展示組"
    }
  ]
}
```

### Admin campaign prize and ticket endpoints

Authenticated admin-only prize and ticket operations scoped to one campaign:

- `GET /api/admin/campaigns/{campaignId}/prizes`
- `POST /api/admin/campaigns/{campaignId}/prizes`
- `PATCH /api/admin/campaigns/{campaignId}/prizes/{prizeId}`
- `GET /api/admin/campaigns/{campaignId}/tickets`
- `POST /api/admin/campaigns/{campaignId}/tickets/generate`

Example prize create/update request:

```json
{
  "rank": "A",
  "name": "壓克力展示組",
  "description": "主視覺展示組。",
  "imageUrl": "https://example.com/prize.png",
  "originalQuantity": 6,
  "sortOrder": 1,
  "lastPrize": false
}
```

`GET /tickets` supports `status`, `q`, and `limit` query parameters and returns serial number, linked prize, ticket status, draw id, masked drawn-by email, and draw timestamp for admin inspection.

Example ticket row:

```json
{
  "id": 52,
  "campaignId": 12,
  "prizeId": 7,
  "serialNumber": "STAR_COLLECTION_VOL_2-0001",
  "status": "DRAWN",
  "statusLabel": "已抽出",
  "prizeRank": "A",
  "prizeName": "壓克力展示組",
  "drawId": 9001,
  "drawnByUserId": 7,
  "drawnByDisplayName": "Lucky 玩家",
  "drawnByEmail": "pl***@e***.com",
  "drawnAt": "2026-07-05T04:00:00Z"
}
```

The ticket generation MVP fills missing `AVAILABLE` tickets for non-last-prize rows, keeps existing ticket mappings, and syncs campaign `totalTickets` / `remainingTickets` from generated tickets. Last-prize rows are managed as prizes but do not generate ordinary tickets. Prize updates reject quantities lower than existing generated tickets. After a campaign is `LIVE`, `PAUSED`, `SOLD_OUT`, or `ENDED`, prize create/update and ticket generation requests return `400 CAMPAIGN_PRIZES_LOCKED`; blocked attempts write `ADMIN_PRIZE_CHANGE_BLOCKED` or `ADMIN_TICKETS_GENERATION_BLOCKED` audit records.

### Admin prize library endpoint

Authenticated admin-only cross-campaign prize list operation:

- `GET /api/admin/prizes`

Supported query parameters:

- `campaignStatus`: `DRAFT`, `SCHEDULED`, `LIVE`, `SOLD_OUT`, `PAUSED`, or `ENDED`.
- `rank`: exact rank match, case-insensitive.
- `lastPrize`: `true` or `false`.
- `q`: keyword matched against prize name/rank/description or campaign slug/title.
- `limit`: clamped to 1-500 rows.

The response includes campaign context plus generated, available, and drawn ticket counts so `/admin/prizes` can act as an independent prize library while edits remain scoped through the campaign editor.

### Admin upload endpoints

Authenticated admin-only image upload operations:

- `POST /api/admin/uploads/images`

The request must be `multipart/form-data` with one `file` part. The MVP stores images under the configurable local upload directory and returns a site-relative URL that can be pasted into campaign, prize, or banner image fields.

Supported image formats:

- `image/jpeg`
- `image/png`
- `image/webp`

The backend validates both the declared `Content-Type` and file magic bytes, rejects mismatches, and limits image size with `luckybox.upload.max-image-size-bytes` / `LUCKYBOX_UPLOAD_MAX_IMAGE_SIZE_BYTES` (default 2 MB).

Example response:

```json
{
  "url": "/uploads/images/20260702/77b3f43f-7b60-4d58-a2f7-37f0a64008e6.png",
  "contentType": "image/png",
  "size": 123456,
  "filename": "77b3f43f-7b60-4d58-a2f7-37f0a64008e6.png"
}
```

Uploaded files are publicly readable by `GET /uploads/**`; upload itself still requires an admin session. Unsupported declared MIME types return `UPLOAD_IMAGE_TYPE_NOT_ALLOWED`, content mismatches return `UPLOAD_IMAGE_CONTENT_MISMATCH`, and oversized images return `UPLOAD_FILE_TOO_LARGE`.

The admin Banner, campaign media, and campaign prize forms expose this endpoint through an upload control next to the existing URL field, so operators can either paste an external URL or upload a local image and let the form fill the returned `/uploads/**` path.

### Admin shipment endpoints

Authenticated admin-only shipment operations:

- `GET /api/admin/shipments`
- `PATCH /api/admin/shipments/{shipmentId}`
- `POST /api/admin/shipments/{shipmentId}/resolve`

Supported list query parameters:

- `status`: `REQUESTED`, `SHIPPED`, `DELIVERED`, `RETURNED`, or `EXCHANGED`.

Example update request:

```json
{
  "status": "SHIPPED",
  "carrier": "黑貓宅急便",
  "trackingNumber": "TA123456789",
  "adminNote": "已交寄"
}
```

When a shipment is updated to `SHIPPED` or `DELIVERED`, the related `UserPrize` rows are moved to the same user-facing prize status. `resolve` handles defect/return support flows for dispatched shipments: `RETURNED` moves prizes back to the member prize box and clears the shipment link, while `EXCHANGED` marks prizes as exchange handling. Non-admin users receive `ADMIN_REQUIRED`.

Example resolve request:

```json
{
  "resolution": "RETURNED",
  "reason": "包裹破損退回"
}
```

### Admin payment order endpoints

Authenticated admin-only payment order operations:

- `GET /api/admin/payment-orders`
- `GET /api/admin/payment-orders/{orderId}`
- `POST /api/admin/payment-orders/{orderId}/refund`

Supported list query parameters:

- `status`: `PENDING`, `PAID`, `FAILED`, `CANCELED`, or `REFUNDED`.
- `provider`: exact provider match, case-insensitive.
- `q`: keyword matched against order id, user email, user display name, or merchant trade number.

Example list row:

```json
{
  "id": 18,
  "userId": 7,
  "userDisplayName": "Lucky 玩家",
  "maskedUserEmail": "pl***@e***.com",
  "provider": "MOCK",
  "merchantTradeNo": "MOCK-7-abcdef123456",
  "amount": 500,
  "pointAmount": 500,
  "bonusPointAmount": 50,
  "totalPointAmount": 550,
  "status": "PAID",
  "statusLabel": "已付款",
  "createdAt": "2026-06-17T13:00:00Z",
  "paidAt": "2026-06-17T13:00:10Z"
}
```

The MVP list keeps member email masked by default and does not expose full payment provider payloads. Use the detail endpoint to inspect provider payloads and webhook delivery records during support or reconciliation work.

Refunds require a reason, only accept `PAID` orders, mark the order `REFUNDED`, reverse the order's credited cash/bonus points, write negative `REFUND` wallet ledger rows, and record `ADMIN_PAYMENT_REFUNDED` audit. For a two-step review flow, create a pending approval request through the admin approval endpoints below.

Example detail response:

```json
{
  "order": {
    "id": 18,
    "userId": 7,
    "userDisplayName": "Lucky 玩家",
    "maskedUserEmail": "pl***@e***.com",
    "provider": "MOCK",
    "merchantTradeNo": "MOCK-7-abcdef123456",
    "amount": 500,
    "pointAmount": 500,
    "bonusPointAmount": 50,
    "totalPointAmount": 550,
    "status": "PAID",
    "statusLabel": "已付款",
    "createdAt": "2026-06-17T13:00:00Z",
    "paidAt": "2026-06-17T13:00:10Z"
  },
  "providerPayload": "{\"provider\":\"MOCK\",\"eventId\":\"evt_001\"}",
  "webhookEvents": [
    {
      "provider": "MOCK",
      "eventId": "evt_001",
      "merchantTradeNo": "MOCK-7-abcdef123456",
      "status": "PAID",
      "amount": 500,
      "processed": true,
      "message": "OK",
      "createdAt": "2026-06-17T13:00:08Z",
      "processedAt": "2026-06-17T13:00:08Z",
      "rawPayload": "{\"provider\":\"MOCK\",\"eventId\":\"evt_001\"}"
    }
  ]
}
```

### Payment reconciliation script

Run the local SQLite reconciliation report from the project root:

```sh
backend/scripts/reconcile-payments.sh --db backend/data/luckybox-dev.sqlite
```

Use `--strict` in CI or operational checks when the command should fail with exit code 2 if issues are found:

```sh
backend/scripts/reconcile-payments.sh --strict --db backend/data/luckybox-dev.sqlite
```

The MVP report summarizes payment orders by provider/status, webhook events by provider/status/message, and flags common finance-risk conditions:

- `PAID_LEDGER_MISMATCH`: paid order cash/bonus points do not match wallet ledger top-up rows.
- `TERMINAL_ORDER_HAS_TOP_UP_LEDGER`: failed or canceled order has positive top-up ledger rows.
- `PROCESSED_PAID_WEBHOOK_ORDER_NOT_PAID`: processed paid webhook exists but the order is not paid/refunded.
- `PROCESSED_TERMINAL_WEBHOOK_STATUS_MISMATCH`: processed failed/canceled webhook does not match the order status.
- `WEBHOOK_AMOUNT_MISMATCH`: webhook amount differs from payment order amount, or the webhook was recorded as `AMOUNT_MISMATCH`.
- `WEBHOOK_ORDER_NOT_FOUND`: webhook event does not match any payment order.

Provider CSV exports can be compared against local `payment_orders` with:

```sh
backend/scripts/reconcile-provider-payments.py \
  --strict \
  --db backend/data/luckybox-dev.sqlite \
  --provider ECPAY \
  --file ecpay-report.csv \
  --merchant-trade-no-column MerchantTradeNo \
  --amount-column TotalAmount \
  --status-column RtnCode \
  --event-id-column TradeNo
```

The provider CSV script can auto-detect common headers, or accept explicit
column names. It treats `PAID`, `SUCCESS`, `SUCCESSFUL`, `COMPLETED`, and `1` as
paid by default; pass `--paid-status`, `--failed-status`, or
`--canceled-status` for provider-specific exports such as JKo Pay status `0`.
It flags:

- `PROVIDER_ROW_ORDER_NOT_FOUND`: provider row has no local order for the selected provider.
- `PROVIDER_AMOUNT_MISMATCH`: provider amount differs from local payment order amount.
- `PROVIDER_STATUS_MISMATCH`: provider status does not match the local terminal status.
- `PROVIDER_ORDER_MISSING_IN_FILE`: local paid/failed/canceled/refunded order is absent from the provider file.
- `PROVIDER_DUPLICATE_ROW`: provider file contains the same merchant trade number more than once.
- `PROVIDER_AMOUNT_INVALID` or `PROVIDER_ROW_MISSING_MERCHANT_TRADE_NO`: provider export row is malformed.

### Admin wallet ledger endpoints

Authenticated admin-only wallet ledger list operation:

- `GET /api/admin/wallet-ledger`

Supported list query parameters:

- `type`: `TOP_UP`, `TOP_UP_BONUS`, `DRAW_SPEND`, `ADJUSTMENT`, `REFUND`, or `COMPENSATION`.
- `pointKind`: `CASH` or `BONUS`.
- `referenceType`: exact reference type match, case-insensitive, such as `PaymentOrder` or `DrawOrder`.
- `q`: keyword matched against ledger/reference id when numeric, otherwise user email, user display name, type, reason, or reference type.

Example list row:

```json
{
  "id": 52,
  "userId": 7,
  "userDisplayName": "Lucky 玩家",
  "maskedUserEmail": "pl***@e***.com",
  "type": "TOP_UP",
  "typeLabel": "現金儲值",
  "amount": 500,
  "pointKind": "CASH",
  "pointKindLabel": "現金點",
  "balanceAfter": 500,
  "referenceType": "PaymentOrder",
  "referenceId": 18,
  "reason": "付款儲值入點",
  "createdByUserId": 7,
  "createdByDisplayName": "Lucky 玩家",
  "createdAt": "2026-06-17T13:00:00Z"
}
```

The MVP list keeps member email masked by default and does not expose full member profiles, payment payloads, or wallet-adjustment approval records.

### Admin coupon endpoints

Authenticated admin-only coupon operations:

- `GET /api/admin/coupons`
- `POST /api/admin/coupons`
- `PATCH /api/admin/coupons/{couponId}`

Supported list query parameters:

- `status`: `DRAFT`, `ACTIVE`, or `ARCHIVED`.
- `type`: `POINT_BONUS`, `DISCOUNT`, or `FREE_SHIPPING`.
- `q`: keyword matched against code, type, or status.

Example request:

```json
{
  "code": "SUMMER100",
  "type": "DISCOUNT",
  "value": 100,
  "minSpend": 500,
  "usageLimit": 100,
  "startsAt": "2026-06-18T00:00:00Z",
  "endsAt": "2026-12-31T23:59:59Z",
  "status": "ACTIVE"
}
```

Example response:

```json
{
  "id": 9,
  "code": "SUMMER100",
  "type": "DISCOUNT",
  "typeLabel": "折扣券",
  "value": 100,
  "minSpend": 500,
  "usageLimit": 100,
  "usedCount": 0,
  "startsAt": "2026-06-18T00:00:00Z",
  "endsAt": "2026-12-31T23:59:59Z",
  "status": "ACTIVE",
  "statusLabel": "啟用",
  "createdAt": "2026-06-18T11:00:00Z",
  "updatedAt": "2026-06-18T11:00:00Z"
}
```

The MVP validates unique 3-32 character coupon codes, type/status values, non-negative spend thresholds, positive usage limits, valid ISO time windows, and writes `ADMIN_COUPON_CREATED` / `ADMIN_COUPON_UPDATED` audit logs. Discount coupon application is available in the authenticated draw-order flow, point-bonus redemption is available in the authenticated account coupon flow, and free-shipping redemption is available in the authenticated shipment flow.

### Admin banner endpoints

Authenticated admin-only banner operations:

- `GET /api/admin/banners`
- `POST /api/admin/banners`
- `PATCH /api/admin/banners/{bannerId}`

Supported list query parameters:

- `status`: `DRAFT`, `ACTIVE`, or `ARCHIVED`.
- `position`: `HOME_HERO` or `HOME_SECTION`.
- `q`: keyword matched against title, image URL, or href.

Example request:

```json
{
  "title": "夏季新品主視覺",
  "imageUrl": "https://images.example.com/summer-hero.png",
  "href": "/news",
  "position": "HOME_HERO",
  "status": "ACTIVE"
}
```

Example response:

```json
{
  "id": 7,
  "title": "夏季新品主視覺",
  "imageUrl": "https://images.example.com/summer-hero.png",
  "href": "/news",
  "position": "HOME_HERO",
  "positionLabel": "首頁主視覺",
  "status": "ACTIVE",
  "statusLabel": "啟用",
  "createdAt": "2026-06-18T10:30:00Z",
  "updatedAt": "2026-06-18T10:30:00Z"
}
```

The MVP validates required title/image URL/position/status fields, accepts image URLs as http(s) or site-relative paths, accepts href as http(s), site-relative paths, or anchors, exposes only `ACTIVE` rows through the public API, and writes `ADMIN_BANNER_CREATED` / `ADMIN_BANNER_UPDATED` audit logs.

### Admin news endpoints

Authenticated admin-only announcement operations:

- `GET /api/admin/news`
- `POST /api/admin/news`
- `PATCH /api/admin/news/{newsId}`

Supported list query parameters:

- `status`: `DRAFT`, `PUBLISHED`, or `ARCHIVED`.
- `q`: keyword matched against title, slug, or content.

Example request:

```json
{
  "title": "出貨批次調整公告",
  "slug": "shipping-update",
  "content": "本週出貨批次將調整為週二與週五。",
  "status": "PUBLISHED",
  "publishedAt": ""
}
```

Example response:

```json
{
  "id": 12,
  "title": "出貨批次調整公告",
  "slug": "shipping-update",
  "content": "本週出貨批次將調整為週二與週五。",
  "status": "PUBLISHED",
  "statusLabel": "已發布",
  "publishedAt": "2026-06-18T10:00:00Z",
  "createdAt": "2026-06-18T09:55:00Z",
  "updatedAt": "2026-06-18T10:00:00Z"
}
```

The MVP validates unique lowercase slug values, accepts statuses `DRAFT`, `PUBLISHED`, and `ARCHIVED`, auto-fills `publishedAt` when publishing without a timestamp, clears `publishedAt` for non-published statuses, and writes `ADMIN_NEWS_CREATED` / `ADMIN_NEWS_UPDATED` audit logs.

### Admin audit log endpoints

Authenticated admin-only audit log list operation:

- `GET /api/admin/audit-logs`

Supported list query parameters:

- `action`: exact action match, case-insensitive, such as `ADMIN_USER_STATUS_UPDATED`.
- `entityType`: exact entity type match, case-insensitive, such as `User`, `Shipment`, or `PaymentOrder`.
- `actorRole`: `SYSTEM`, `SUPER_ADMIN`, `ADMIN`, `OPERATOR`, `CUSTOMER_SERVICE`, or `USER`.
- `q`: keyword matched against audit id, actor id, entity id, action, entity type, before/after state, actor role, actor email, or actor display name.
- `limit`: result limit from 1 to 200; defaults to 100.

Audit logs are append-only from the admin API. `DELETE /api/admin/audit-logs/{id}` is explicitly rejected with `405 AUDIT_LOG_IMMUTABLE`, so ordinary back-office users can review records but cannot hard-delete or soft-delete them.

Example list row:

```json
{
  "id": 88,
  "actorId": 1,
  "actorDisplayName": "LuckyBox Admin",
  "maskedActorEmail": "ad***@l***.local",
  "actorRole": "SUPER_ADMIN",
  "actorRoleLabel": "超級管理員",
  "action": "ADMIN_USER_STATUS_UPDATED",
  "actionLabel": "後台更新會員狀態",
  "entityType": "User",
  "entityTypeLabel": "會員",
  "entityId": "7",
  "beforeState": null,
  "afterState": "{\"status\":\"SUSPENDED\"}",
  "ipAddress": null,
  "createdAt": "2026-06-18T10:00:00Z"
}
```

The MVP list masks actor email and caps responses at 200 rows. It is intended for operational traceability, not export or compliance archiving.

### Admin draw order endpoints

Authenticated admin-only draw order operations:

- `GET /api/admin/draw-orders`
- `GET /api/admin/draw-orders/{orderId}`

Supported list query parameters:

- `status`: `PENDING`, `COMPLETED`, `FAILED`, or `REFUNDED`.
- `campaignSlug`: exact campaign slug.
- `q`: keyword matched against order id, user email, user display name, campaign slug, or campaign title.

Example list row:

```json
{
  "id": 12,
  "userId": 7,
  "userDisplayName": "Lucky 玩家",
  "maskedUserEmail": "pl***@e***.com",
  "campaignSlug": "star-collection-vol-1",
  "campaignTitle": "星光收藏盒 Vol.1",
  "quantity": 2,
  "pointSpent": 200,
  "status": "COMPLETED",
  "statusLabel": "完成",
  "resultCount": 2,
  "prizeSummary": "A賞 壓克力展示組、B賞 收藏立牌",
  "createdAt": "2026-06-14T05:00:00Z",
  "completedAt": "2026-06-14T05:00:01Z"
}
```

Example detail response:

```json
{
  "id": 12,
  "userId": 7,
  "userDisplayName": "Lucky 玩家",
  "maskedUserEmail": "pl***@e***.com",
  "campaignSlug": "star-collection-vol-1",
  "campaignTitle": "星光收藏盒 Vol.1",
  "quantity": 2,
  "originalPointSpent": 200,
  "discountAmount": 0,
  "pointSpent": 200,
  "couponCode": null,
  "status": "COMPLETED",
  "statusLabel": "完成",
  "resultCount": 2,
  "prizeSummary": "A賞 壓克力展示組、B賞 收藏立牌",
  "idempotencyKey": "draw-uuid",
  "createdAt": "2026-06-14T05:00:00Z",
  "completedAt": "2026-06-14T05:00:01Z",
  "results": [
    {
      "id": 31,
      "resultIndex": 1,
      "ticketSerialNumber": "STAR-0001",
      "prizeRank": "A",
      "prizeName": "壓克力展示組",
      "lastPrize": false,
      "randomProof": "server-random:STAR-0001",
      "createdAt": "2026-06-14T05:00:01Z"
    }
  ],
  "ledgerRows": [
    {
      "id": 44,
      "type": "DRAW_SPEND",
      "typeLabel": "抽賞扣點",
      "amount": -200,
      "pointKind": "CASH",
      "pointKindLabel": "現金點",
      "balanceAfter": 800,
      "reason": "抽賞扣現金點",
      "createdBy": 7,
      "createdAt": "2026-06-14T05:00:01Z"
    }
  ]
}
```

The list and detail responses keep member email masked by default and do not expose shipment addresses or full member profiles. Missing draw orders return `DRAW_ORDER_NOT_FOUND`.

### Admin user endpoints

Authenticated admin-only member operations:

- `GET /api/admin/users`
- `GET /api/admin/users/{userId}`
- `POST /api/admin/users/{userId}/notes`
- `POST /api/admin/users/{userId}/compensation`
- `PATCH /api/admin/users/{userId}/status`
- `PATCH /api/admin/users/{userId}/role`

Supported list query parameters:

- `status`: `ACTIVE`, `SUSPENDED`, or `DELETED`.
- `role`: `USER`, `CUSTOMER_SERVICE`, `OPERATOR`, `ADMIN`, or `SUPER_ADMIN`.
- `q`: keyword matched against email, display name, or phone.

Example list row:

```json
{
  "id": 7,
  "maskedEmail": "pl***@e***.com",
  "displayName": "Lucky 玩家",
  "maskedPhone": "***678",
  "role": "USER",
  "roleLabel": "會員",
  "status": "ACTIVE",
  "statusLabel": "啟用",
  "vipLevel": "REGULAR",
  "cashPointBalance": 500,
  "bonusPointBalance": 50,
  "drawOrderCount": 3,
  "prizeCount": 2,
  "shipmentCount": 1,
  "createdAt": "2026-06-14T02:00:00Z",
  "lastLoginAt": "2026-06-14T03:00:00Z"
}
```

Example status request:

```json
{
  "status": "SUSPENDED"
}
```

Member detail returns wallet/activity stats, masked contact data, masked addresses, recent ledger entries, recent prizes, and support notes by default. Call `GET /api/admin/users/{id}?reveal=true` only when a support case requires full contact/address data; that response sets `piiRevealed=true`, returns unmasked PII, and writes an `ADMIN_MEMBER_DETAIL_VIEWED` audit record. Notes and compensation are append-only support actions; compensation credits bonus points, writes a `COMPENSATION` wallet ledger row, records audit, and notifies the member.

The MVP list returns masked contact fields by default. Status changes only allow `ACTIVE` and `SUSPENDED`, write `ADMIN_USER_STATUS_UPDATED` audit logs, and reject self-updates or `SUPER_ADMIN` targets. Role changes require `SUPER_ADMIN`, can assign `USER`, `CUSTOMER_SERVICE`, `OPERATOR`, or `ADMIN`, and write `ADMIN_USER_ROLE_UPDATED`.

### Admin approval request endpoints

Authenticated admin-only approval operations:

- `GET /api/admin/approval-requests`
- `POST /api/admin/approval-requests/wallet-adjustments`
- `POST /api/admin/approval-requests/payment-refunds/{orderId}`
- `POST /api/admin/approval-requests/compensations/{userId}`
- `POST /api/admin/approval-requests/{requestId}/approve`
- `POST /api/admin/approval-requests/{requestId}/reject`

Supported list query parameters:

- `status`: `PENDING`, `APPROVED`, or `REJECTED`.
- `type`: `WALLET_ADJUSTMENT`, `PAYMENT_REFUND`, or `COMPENSATION`.

Approval requests store the proposed action payload, requester, reviewer, status, and result entity. Approval requires `SUPER_ADMIN`; approving executes the underlying wallet adjustment, payment refund, or member compensation service in a transaction, then records `ADMIN_APPROVAL_APPROVED`. Rejecting records `ADMIN_APPROVAL_REJECTED` and does not execute the action.

Example response:

```json
{
  "id": 3,
  "type": "WALLET_ADJUSTMENT",
  "typeLabel": "點數調整",
  "status": "PENDING",
  "statusLabel": "待審核",
  "entityType": "User",
  "entityId": "7",
  "reason": "活動補點需複核",
  "payloadJson": "{\"userId\":7,\"pointKind\":\"BONUS\",\"amount\":120,\"reason\":\"活動補點需複核\"}",
  "requestedBy": 1,
  "requestedByDisplayName": "LuckyBox Admin",
  "reviewedBy": null,
  "reviewedByDisplayName": null,
  "resultEntityType": null,
  "resultEntityId": null,
  "createdAt": "2026-07-03T05:00:00Z",
  "reviewedAt": null,
  "updatedAt": "2026-07-03T05:00:00Z"
}
```

### Admin settings endpoint

Authenticated admin-only runtime settings summary:

- `GET /api/admin/settings`

The response groups non-secret runtime, security, payment, mail, and promo settings for `/admin/settings`. Sensitive values such as payment hash keys, webhook secrets, SMTP passwords, and provider credentials are not returned.

All mutation APIs will validate server-side state and reject frontend-provided price or result decisions.
