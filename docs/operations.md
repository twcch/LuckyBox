# Operations

## Local Environment

Copy `.env.example` for local settings. Do not commit real secrets.

Environment files are intentionally ignored at the repository root and inside `backend/` / `frontend/`.
Keep real values such as payment secrets, SMTP passwords, database URLs with credentials, and provider keys in local environment files or the deployment secret store. Commit only `.env.example` placeholders.

The backend SQLite database defaults to:

```text
backend/data/luckybox-dev.sqlite
```

The development profile seeds one admin account and three public campaigns when `luckybox.seed.enabled=true`:

```text
admin@luckybox.local / ChangeMe123!
```

The default SQLite connection enables WAL mode, foreign keys, and a 5000ms busy timeout. Override the database location with `LUCKYBOX_DB_URL` when needed.

Uploaded images default to `./uploads` and are served from `/uploads/**`. Override the storage path with `LUCKYBOX_UPLOAD_DIR`; adjust the per-image limit with `LUCKYBOX_UPLOAD_MAX_IMAGE_SIZE_BYTES` (default `2097152`, 2 MB). Keep this directory outside ephemeral storage in any shared or production-like environment.

Daily check-in rewards use `LUCKYBOX_DAILY_CHECK_IN_BONUS` for the base daily
bonus and `LUCKYBOX_DAILY_CHECK_IN_STREAK_BONUSES` for consecutive-day tier
bonuses. The tier format is comma-separated `streak:bonus` pairs, for example
`3:30,7:80,14:150,30:500`.

## Production Notes

- Use [Launch Readiness](launch-readiness.md) and
  [Launch Sign-Off Register](launch-signoff-register.md) as the production
  gate. They link the payment-provider decision, required env flags, SOPs, smoke
  tests, backup flow, and external sign-offs.
- Choose one deployment shape before launch:
  - Single package: build Vue, then run `./mvnw -Psingle-package -DskipTests package` so Spring Boot serves the static assets from the jar.
  - Split package: deploy Vue static assets on Nginx/Apache/CDN and Spring Boot as a separate API service.
- Supported production targets are VPS, Docker Compose, or a PaaS that provides persistent disk or external storage for SQLite and uploads.
- Place SQLite and uploaded files on persistent storage. Do not run production data from an ephemeral container filesystem.
- Configure backups before accepting real orders:
  - SQLite database backup at least daily, plus an on-demand backup before migrations.
  - Uploaded image directory backup on the same retention policy as the database.
  - Restore drill before launch: restore DB + uploads into a staging environment and run smoke tests.
- Keep payment provider keys, SMTP credentials, database URLs, and admin bootstrap secrets server-side only.
- Set `LUCKYBOX_MAIL_ENABLED=true` with SMTP host, port, username, password, and `LUCKYBOX_MAIL_FROM` in production if password reset and shipment status emails should be delivered. When mail is disabled or no SMTP bean is available, LuckyBox logs mail metadata only and keeps in-app notifications working.
- If Spring Boot runs behind a trusted reverse proxy, load balancer, or CDN, set `LUCKYBOX_RATELIMIT_TRUST_FORWARDED_HEADERS=true` so login, registration, password reset, draw, and payment-webhook rate limits use the first client address from `X-Forwarded-For` / `Forwarded`. Keep it `false` for direct public exposure.
- Set `SPRING_PROFILES_ACTIVE=prod`; production disables dev seed and mock payment routes by default.
- Enable admin 2FA before production launch. The `/admin/security` enrollment flow shows both a TOTP QR code and the manual secret, and high-risk actions should remain restricted through the approval center.
- Configure monitoring:
  - Error reporting with Sentry or equivalent log aggregation.
  - Spring Boot Actuator exposes `/actuator/health` publicly for uptime checks; metrics remain protected by application security.
  - Uptime monitor for the public site and `/api/health`.
  - Product analytics through Plausible, PostHog, or the built-in visitor/event tables.
- Review legal, privacy, payment, consumer protection, trademark, and IP licensing requirements before launch.
- Run the production gate before launch:

```sh
scripts/check-launch-readiness.sh --env .env.production
scripts/generate-launch-evidence-template.sh --env .env.production --out launch-evidence.md
scripts/backup-luckybox.sh --db /path/to/luckybox.sqlite --uploads /path/to/uploads --out /path/to/backups
scripts/smoke-test.sh https://luckybox.example.com
backend/scripts/reconcile-payments.sh --strict --db /path/to/luckybox.sqlite
backend/scripts/reconcile-provider-payments.py --strict --db /path/to/luckybox.sqlite --provider ECPAY --file /path/to/ecpay-report.csv --merchant-trade-no-column MerchantTradeNo --amount-column TotalAmount --status-column RtnCode --event-id-column TradeNo
```

## Launch SOP Checklist

- Customer support SOP: [docs/sops/customer-support.md](sops/customer-support.md).
- Shipping SOP: [docs/sops/shipping.md](sops/shipping.md).
- Emergency takedown SOP: [docs/sops/emergency-takedown.md](sops/emergency-takedown.md).
- Refund/compensation SOP: [docs/sops/refund-compensation.md](sops/refund-compensation.md).
- Smoke test: register/login, top up, draw, coupon use, prize box shipment, admin order/detail, approval flow, and audit log review.

## Incident Priorities

1. Stop affected campaign or payment flow.
2. Preserve audit logs and raw provider payloads.
3. Reconcile wallet ledger, payment order, draw order, ticket, and prize ownership state.
4. Publish customer-facing notices when needed.
