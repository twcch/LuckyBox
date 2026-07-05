# Testing

## Frontend

```sh
cd frontend
npm run lint
npm run test
npm run build
```

Optional E2E:

```sh
cd frontend
npm run test:e2e
```

Headless or non-npm environments can override the dev server command, for example:

```sh
PLAYWRIGHT_HEADLESS=true PLAYWRIGHT_WEB_SERVER_COMMAND='pnpm run dev -- --host 127.0.0.1' pnpm exec playwright test --project=chromium
```

Responsive UI checks should cover:

- Public pages: `/`, `/design-system`, `/status`, `/leaderboard`, `/news`, `/news/:slug`, `/faq`, `/contact`, `/terms`, `/fairness`, `/shipping-policy`, and `/privacy` at 1440px, 768px, and 390px.
- Auth pages: `/login`, `/register`, `/forgot-password`, `/reset-password`, and authenticated `/account` at 1440px, 768px, and 390px.
- Campaign discovery: `/` campaign search, filters, sorting, pagination, and empty state at 1440px, 768px, and 390px.
- Wallet and checkout: authenticated `/account/wallet`, `/payment/mock/:orderId` sandbox checkout success, wallet balance update, ledger rows, and header LP update at 1440px, 768px, and 390px.
- Draw flow: authenticated `/kuji/:slug`, draw quantity controls, discount coupon code entry, successful draw result cards with original cost/discount/final spend, wallet balance refresh, prize quantity/probability refresh, and insufficient-balance or invalid-coupon error states at 1440px, 768px, and 390px.
- Prize and shipment flow: authenticated `/account/prizes`, shipment notifications, status/campaign filters, multi-select shipment request, address selection, free-shipping coupon selection, shipment history refresh, duplicate shipment prevention, and admin `/admin/shipments` status/tracking updates at 1440px, 768px, and 390px.
- Admin console: authenticated admin `/admin`, `/admin/login`, shared admin layout navigation, dashboard metrics, requested shipments, recent audit activity, `/admin/campaigns` campaign create/edit/publish/pause flows, `/admin/campaigns/:id/tickets` prize/ticket generation flows, `/admin/shipments` shipment handling, `/admin/users` member search/filter/status flows, `/admin/draws` draw-order search/filter/detail flows, `/admin/orders` payment-order search/filter flows, `/admin/wallet-ledger` wallet-ledger search/filter flows, `/admin/news` announcement create/edit/filter flows, `/admin/banners` banner create/edit/filter flows, `/admin/coupons` coupon create/edit/filter flows, and `/admin/audit-logs` audit-log search/filter flows at 1440px, 768px, and 390px.
- Admin image upload controls should be checked on `/admin/banners` and `/admin/campaigns`: selecting a JPG/PNG/WebP should fill the returned `/uploads/**` URL in the Banner image, campaign cover, campaign Banner, or prize image field, while locked prize editing keeps the upload control disabled.
- Content and coupon flows: public `/news` and `/news/:slug` published announcement browsing, homepage `HOME_HERO` banner rendering and static-image fallback, authenticated `/account/coupons` available coupon browsing and point-bonus coupon redemption, and draw-flow discount coupon application at 1440px, 768px, and 390px.
- Public/member/admin E2E: Playwright mocks anonymous/public/auth/wallet/draw/prize/shipment/admin-campaign APIs and verifies homepage hero, campaign discovery, URL-backed search, LIVE strip, popular campaigns, public news navigation, registration, login, mock-checkout top-up, authenticated campaign draw with discount/result/wallet/remaining-ticket refresh, prize-box status filtering, shipment request with free-shipping coupon, and admin campaign create/prize/ticket/dry-run/publish flow in Chromium.

Confirm there is no horizontal overflow, no obvious text overflow, no component overlap, and no browser console errors.

Frontend unit tests cover reusable admin image upload behavior, shared API client defaults/param compaction, and `/status` health-check rendering for both reachable and unreachable backend API states.

## Backend

```sh
cd backend
./mvnw test
./mvnw package
```

The current backend suite verifies:

- Flyway can migrate the SQLite schema.
- Dev seed data is idempotent and contains the admin user, three campaigns, prizes, and matching ticket totals.
- SQLite hot-path indexes exist with the expected columns for campaign status, ticket campaign/status, draw-order user, and wallet-ledger user lookups.
- Environment secret policy tests verify root/backend/frontend ignore rules for `.env` files, local SQLite data, uploads, and logs; `.env.example` keeps sensitive values as placeholders, and the production profile keeps dev seed/mock payment routes off by default.
- Request body validation policy tests verify every `@RequestBody` parameter is protected by Jakarta Bean Validation before service logic; content/admin/payment/shipment/wish DTOs also define basic required, length, and numeric-bound constraints.
- Calculation unit tests verify public campaign remaining-rate/probability rounding, last-prize exclusion from regular draw probability and ticket totals, and discount coupon capping so final draw spend cannot go below zero.
- Admin image upload APIs enforce admin-only access, store allowed JPG/PNG/WebP files in the configured upload directory, serve the returned `/uploads/**` URL, and reject oversized files, unsupported declared MIME types, or content-type/magic-byte mismatches.
- Public campaign list/detail APIs return seeded data, support search/filter/sort/pagination, derive public remaining counts from `AVAILABLE` tickets even when stored counters are stale, expose source/rights and age-restriction disclosure fields, expose the `/api/campaigns/{slug}/probabilities` compatibility response, and return structured not-found errors.
- Public news APIs return published announcements only, expose announcement detail by slug, and return structured not-found errors for drafts, archived items, or missing slugs.
- Public banner APIs return active banners only, support position filtering, and let the homepage fall back to the bundled static banner when no active banner exists.
- Public leaderboard APIs return masked live draws, popular campaign ranking, lucky member ranking, campaign-specific draw history, and the leaderboard service unit tests verify the short TTL cache for popular campaigns.
- Global exception handling returns a generic 500 `INTERNAL_ERROR` response without leaking exception messages, Java class names, or stack trace text to API clients.
- Rate limit tests verify per-IP auth/payment-webhook buckets, per-user draw buckets, 429 `RATE_LIMITED` responses, and trusted proxy handling: forwarded headers are ignored by default, while `LUCKYBOX_RATELIMIT_TRUST_FORWARDED_HEADERS=true` uses the first `X-Forwarded-For` / `Forwarded` client address.
- Account coupon APIs return currently available active coupons only, hide expired, archived, draft, usage-exhausted, or already-used coupons, redeem point-bonus coupons into bonus-point wallets, write `COUPON_BONUS` ledger rows, and reject repeated or wrong-type redemptions.
- Auth APIs can register, login, keep a session, update profile, logout, and protect anonymous requests.
- Security principal unit tests lock shared authenticated/admin/super-admin role checks and their API error codes.
- Admin 2FA APIs can return status, generate setup secrets with `otpauth://` URIs and PNG QR code data URIs, enable TOTP after a valid 6-digit code, require TOTP during login once enabled, disable TOTP with a valid current code, and reject non-admin access.
- Account address APIs can create, update, list, and delete the current user's addresses.
- Account check-in APIs can report today's status, grant the configured base bonus once per Taipei date, keep duplicate same-day requests idempotent, calculate consecutive-day streak bonuses, store the awarded amount on `daily_check_ins`, and write matching `CHECK_IN_BONUS` ledger rows.
- Wallet APIs can create mock, ECPay, LINE Pay, and JKo Pay payment orders, confirm mock checkout through a server-side signed webhook, generate ECPay AioCheckOut fields, include optional ECPay `CreditInstallment` checkout periods, create LINE Pay/JKo Pay redirect checkout responses, verify ECPay `CheckMacValue`, accept valid ECPay ReturnURL callbacks, process LINE Pay confirm redirects, process JKo Pay confirm/result callbacks, reject invalid checksum or amount-mismatch callbacks, ignore simulated paid callbacks by default, complete legacy mock payments, credit cash/bonus points, receive point-bonus coupon redemptions, write ledger rows, refresh `/api/auth/me` balances, and keep repeated payment completion idempotent; the `/api/me`, `/api/wallet`, `/api/wallet/ledger`, `/api/payments/top-up`, `/api/payments/mock/complete`, and `/api/addresses` compatibility aliases proxy the current account endpoints; the mock-payment disabled profile rejects legacy complete, checkout confirm, and mock webhook paths without crediting wallets or recording webhook events.
- Draw APIs can spend points, randomly draw tickets, decrement prize/campaign remaining counts, create draw orders/results/user prizes, return current-member draw order/result detail through `/api/account/draw-orders/{orderId}`, `/api/draw-orders/{orderId}`, and `/api/draw-results/{resultId}`, apply active discount coupons, reject insufficient balance, remaining tickets, expired/over-quota/already-used coupons, below-threshold coupon attempts, and out-of-range draw quantities through Bean Validation; they also compute point spend from DB campaign price while ignoring client-submitted pricing fields, record coupon usage, and keep repeated draw requests idempotent without reusing coupons.
- Prize box APIs can list/filter the current user's prizes, create shipment requests from selected `IN_BOX` prizes, apply available free-shipping coupons, move `UserPrize` rows to `SHIPMENT_REQUESTED`, list shipment history, and reject duplicate shipment attempts or wrong-type shipment coupons.
- Admin dashboard APIs can enforce admin-only access and return operational metrics, requested shipment queue, and recent audit activity.
- Admin campaign APIs can enforce admin-only access, list/search/filter campaign master data, create campaigns including GK source type, update campaign fields, reject duplicate slugs, preserve sold-ticket counts, manage campaign prizes, generate missing tickets, list full campaign tickets with masked drawn-by email, expose the cross-campaign prize library, protect prize quantities below generated tickets, require commercial-use confirmation, official-license confirmation for official campaigns, age-restriction completeness, and available tickets before publish, pause live/scheduled campaigns, block sensitive campaign/prize/ticket edits after a campaign is live or sold, verify public visibility changes, and write audit logs for both successful and blocked sensitive operations.
- Admin member APIs can enforce admin-only access, list members with masked contact fields, return masked member detail by default, reveal full email/phone/address only with `reveal=true`, and write `ADMIN_MEMBER_DETAIL_VIEWED` audit logs only for full PII reveal.
- Admin draw-order APIs can enforce admin-only access, list draw orders with masked member email, filter by status/campaign/keyword, and return single-order detail with idempotency key, draw results, ticket serials, random proof, and related point ledger rows without exposing full member contact data.
- Admin shipment APIs can list requested shipments, enforce admin-only access, require tracking fields when marking a shipment shipped, update shipment status/carrier/tracking/admin note, move related `UserPrize` rows to `SHIPPED`, create account shipment notifications, mark notifications read, and write an audit log. Notification unit tests additionally cover shipment status email delivery through `EmailService`, including no duplicate send when the in-app notification already exists.
- Admin wallet ledger APIs can enforce admin-only access, list wallet ledger rows with masked member email, filter by type/point kind/reference/keyword, label point movement types, and reject invalid filter values.
- Admin news APIs can enforce admin-only access, create/list/filter/update announcements, reject duplicate slugs or invalid publication timestamps, publish items to the public news API, archive items from public visibility, and write audit logs.
- Admin banner APIs can enforce admin-only access, create/list/filter/update banners, validate image URLs, expose active `HOME_HERO` banners to the public API, archive banners from public visibility, and write audit logs.
- Admin coupon APIs can enforce admin-only access, create/list/filter/update coupons, reject duplicate codes or invalid periods, expose active available coupons to account users, archive coupons from member visibility, support draw-flow discount application, point-bonus redemption, and shipment free-shipping redemption, and write audit logs.
- Admin audit log APIs can enforce admin-only access, list audit logs with masked actor email, filter by action/entity/actor role/keyword, cap response size, reject invalid filter values, and explicitly reject admin delete attempts with `AUDIT_LOG_IMMUTABLE` while preserving the audit row.
- Admin settings APIs can enforce admin-only access and return non-secret runtime, security, payment, mail, and promo configuration summaries without exposing payment secrets, SMTP passwords, or webhook keys.
- Payment reconciliation script tests run both the SQLite ledger/webhook reconciliation script and the provider CSV reconciliation script in strict mode against temporary clean and dirty databases, verifying no-issue success, amount mismatch, duplicate provider rows, missing provider rows, and unknown provider-export orders.
- SQLite can handle short concurrent transactional writes with WAL and busy timeout enabled.

## External Fixture Follow-up

- Add real provider export fixtures after production merchant portals provide official ECPay, LINE Pay, or JKo Pay settlement samples.
