# LuckyBox Launch Readiness

This document is the production launch gate for LuckyBox. It converts the
remaining Phase 14 work into repeatable checks, SOP references, and explicit
external sign-offs.

Use [Launch Sign-Off Register](launch-signoff-register.md) for the owner,
evidence, and env-flag mapping behind each external decision.

## Scope

- This is an engineering and operations checklist, not legal advice.
- Production launch remains blocked until legal review, payment provider
  contract activation, production product/material rights, and deployment
  ownership are signed off by the business owner.
- Real secrets must live in the deployment secret store or local env files only.
  The repository keeps placeholders in `.env.example`.

## Payment Provider Decision

Engineering default for the first real Taiwan payment integration:

1. ECPay first.
2. NewebPay as fallback if contract, fee, settlement, or integration constraints
   make ECPay unsuitable.
3. LINE Pay and JKo Pay adapters are available as Phase 2 redirect checkout
   options, but require merchant credentials and provider callback tests before
   production use. Invoice automation and convenience-store collection remain
   later expansion.

References used for the decision:

- [ECPay Developers](https://developers.ecpay.com.tw/)
- [ECPay All-in-One Payment documentation](https://developers.ecpay.com.tw/2509/)
- [NewebPay API download page](https://www.newebpay.com/website/Page/content/download_api)
- [ECPay 2025 API adjustment announcement](https://www.ecpay.com.tw/announcement/DetailAnnouncement?nID=5632)

The codebase includes an ECPay AioCheckOut adapter:

- Member top-up can create `ECPAY` payment orders when `LUCKYBOX_PAYMENT_PROVIDER=ECPAY`.
- The frontend requests `/api/account/payment-orders/:id/ecpay-checkout` and
  submits the returned hidden form fields to ECPay.
- Optional ECPay credit-card installment periods can be enabled with
  `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT` after ECPay approves the periods.
- ECPay server callbacks post to `/api/webhooks/payment/ecpay` as
  `application/x-www-form-urlencoded`.
- Callback handling verifies `CheckMacValue`, records webhook events, checks
  amount/order consistency, ignores simulated paid callbacks by default, and
  credits points idempotently only after a valid paid callback.

The codebase also includes LINE Pay and JKo Pay redirect adapters:

- LINE Pay creates a request API payment URL, confirms the transaction id on
  provider redirect, and records confirm events idempotently.
- JKo Pay calls the Entry API, exposes confirm/result callback endpoints, and
  records result callbacks by `tradeNo`.
- Production readiness requires `LUCKYBOX_PAYMENT_LINEPAY_CALLBACK_TESTED=true`
  or `LUCKYBOX_PAYMENT_JKOPAY_CALLBACK_TESTED=true` when those providers are
  selected.

Local development still defaults to the signed mock provider. Production launch
must use the final merchant account credentials and run the selected provider's
staging/merchant dashboard callback test before accepting real money.

## Required Commands

Before production traffic:

```sh
scripts/check-launch-readiness.sh --env .env.production
scripts/generate-launch-evidence-template.sh --env .env.production --out launch-evidence.md
scripts/backup-luckybox.sh --db /path/to/luckybox.sqlite --uploads /path/to/uploads --out /path/to/backups
scripts/smoke-test.sh https://luckybox.example.com
backend/scripts/reconcile-payments.sh --strict --db /path/to/luckybox.sqlite
backend/scripts/reconcile-provider-payments.py --strict --db /path/to/luckybox.sqlite --provider ECPAY --file /path/to/ecpay-report.csv --merchant-trade-no-column MerchantTradeNo --amount-column TotalAmount --status-column RtnCode --event-id-column TradeNo
```

For a single-package deploy:

```sh
cd frontend
npm run build
cd ../backend
./mvnw -Psingle-package -DskipTests package
```

## Production Environment Gate

Required runtime settings:

- `SPRING_PROFILES_ACTIVE=prod`
- `LUCKYBOX_PAYMENT_MOCK_ENABLED=false`
- `LUCKYBOX_DB_URL` points to the production SQLite file on persistent storage.
- `LUCKYBOX_UPLOAD_DIR` points to persistent storage or mounted object-storage
  sync path.
- `LUCKYBOX_APP_BASE_URL` is the public HTTPS origin.
- `LUCKYBOX_RATELIMIT_TRUST_FORWARDED_HEADERS=true` is set when production
  traffic reaches Spring Boot through a trusted proxy/CDN; leave it `false` only
  for direct exposure where forwarded headers must not be trusted.
- `LUCKYBOX_PAYMENT_PROVIDER=ECPAY` for the first implementation unless the
  business owner switches to `NEWEBPAY`.
- `LUCKYBOX_PAYMENT_ECPAY_ENABLED=true`
- Provider merchant id, hash key, hash IV, callback URL, and client return URL
  are set in the secret store.
- If credit-card installment is enabled, `LUCKYBOX_PAYMENT_ECPAY_CREDIT_INSTALLMENT`
  contains only ECPay-approved periods and
  `LUCKYBOX_PAYMENT_ECPAY_INSTALLMENT_CONTRACT_APPROVED=true`.
- SMTP is configured if password reset and shipment status emails are enabled.
- Sentry or equivalent log aggregation is configured.
- Uptime monitor covers the public site, `/api/health`, and `/actuator/health`.
- Backup job and restore drill are completed before accepting paid orders.
- All admin accounts have 2FA enabled through `/admin/security` using the QR-code or manual-secret enrollment flow.

Run `scripts/check-launch-readiness.sh` in CI or manually before the launch
window. It should return zero failures.

Run `scripts/generate-launch-evidence-template.sh` before the final go/no-go
review to create the redacted evidence packet. The generated file is a template
for owners and approval links; it must not contain real payment secrets.

## External Sign-Offs

These items cannot be completed by code alone:

- Legal counsel reviewed terms, privacy, consumer protection copy, refund policy,
  age/minor rules, and campaign disclosures.
- Legal-review feedback has been applied to the public documents.
- Payment provider contract is approved and production merchant account is active.
- Product source, official-license claims, image rights, and commercial-use rights
  are approved for the first official campaigns.
- Company registration, invoice policy, logistics provider, shipping owner,
  convenience-store pickup policy, international shipping policy, preorder policy,
  deployment owner, and rollback owner are decided.
- First official campaign stock, prizes, descriptions, risk disclosures, and
  shipping assumptions are approved by operations.

## SOP References

- [Customer Support SOP](sops/customer-support.md)
- [Shipping SOP](sops/shipping.md)
- [Emergency Takedown SOP](sops/emergency-takedown.md)
- [Refund and Compensation SOP](sops/refund-compensation.md)

## Launch Window Checklist

- Freeze new feature changes.
- Confirm latest backend tests, frontend lint/test/build, Playwright E2E, and
  packaging pass.
- Generate the launch evidence packet and attach owner approvals without real
  secrets.
- Create pre-launch backup with `scripts/backup-luckybox.sh`.
- Run DB migration on staging, then production.
- Run smoke test against production.
- Confirm `LUCKYBOX_PRODUCTION_SMOKE_TEST_DONE=true` only after the production
  smoke-test output is attached to the release record.
- Verify admin login, 2FA, campaign publish checklist, payment order detail,
  approval center, audit log, shipment admin, and customer-facing policy pages.
- Start with small traffic and monitor payment failure rate, draw API errors,
  p95 latency, support queue, and failed/canceled payment orders.
- Keep rollback owner, takedown owner, and customer-support owner available
  during the launch window.
