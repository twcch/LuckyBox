# Architecture

## Current MVP Shape

LuckyBox is split into two deployable workspaces during development:

- `frontend/`: Vue 3 single-page app using Bootstrap 5, Vue Router, Pinia, Axios, Vitest, and Playwright.
- `backend/`: Spring Boot application using Spring MVC, Spring Security, Spring Validation, Spring JDBC, SQLite, Flyway, JUnit 5, Mockito, and MockMvc.

The MVP keeps the frontend and backend separated for local development. For a single deployable jar, build `frontend/dist` first and run the backend with the `single-package` Maven profile; it copies the Vue build output into Spring Boot static resources during packaging.

Phase 1 adds the first usable storefront data path:

- Flyway creates the SQLite schema for users, wallets, campaigns, prizes, tickets, draw orders/results, prize ownership, shipments, coupons, banners, news, and audit logs.
- A dev seed runner inserts one super-admin user, three public campaigns, their prizes, and all available tickets.
- Public campaign APIs expose list and detail data for the Vue storefront.
- The Vue home page loads campaigns from the backend through the Vite `/api` proxy, and `/kuji/:slug` renders detail data from the real API.

## Trust Boundaries

- The frontend never decides price, balance, ticket availability, draw result, or prize ownership.
- The backend owns authentication, authorization, wallet ledger, draw transaction, ticket mutation, audit log, and shipment state.
- SQLite is acceptable for MVP only with conservative transaction design, WAL mode, busy timeout, short draw transactions, and application-level campaign locks in later phases.

## Data Storage

Local SQLite files live under `backend/data/` and are ignored by git. Flyway migrations live under `backend/src/main/resources/db/migration/`.

The default development database URL is `jdbc:sqlite:./data/luckybox-dev.sqlite?busy_timeout=5000`. The backend initializes `journal_mode=WAL`, `busy_timeout=5000`, and `foreign_keys=ON` at startup.
