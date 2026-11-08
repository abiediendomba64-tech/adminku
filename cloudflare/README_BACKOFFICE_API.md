# Adminku Backoffice REST API v1

This change adds `/api/v1/*` to the existing Telegram Worker. The Telegram webhook and command handler remain in `worker.js`; the API is implemented in `backoffice-api.js`.

## Authentication and origin

Every protected API request must send Telegram Mini App **raw** `initData` in `X-Telegram-Init-Data`. The Worker validates the HMAC with `TELEGRAM_BOT_TOKEN` and rejects stale `auth_date` values. Do not send `initDataUnsafe` and do not put the bot token in frontend code.

Set `BACKOFFICE_ORIGIN` as a comma-separated exact origin allowlist only after Cloudflare Pages has a final URL, for example `https://<actual-pages-project>.pages.dev`. Do not guess or use a path in this variable. Without it, the API deliberately returns `503 API_NOT_CONFIGURED`. No origin is committed in this change.

`INIT_DATA_MAX_AGE_SECONDS` is optional and defaults to 3600 seconds. `TELEGRAM_ADMIN_ID` and `TELEGRAM_ADMIN_IDS` remain the only sources of ADMIN privileges. A D1 `users.role` row cannot grant ADMIN. Other assignable roles are `STAFF`, `VIEWER`, and `MEMBER`.

## Routes

All routes are prefixed by `/api/v1`.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/health` | public after origin allowlist | API/D1 health |
| GET | `/auth/me` | authenticated | Current Telegram user and effective role |
| GET | `/settings` | ADMIN, STAFF, VIEWER | Read-only, non-secret runtime settings |
| GET | `/dashboard` | ADMIN, STAFF, VIEWER | Aggregated counters and recent records |
| GET, POST | `/tickets` | ADMIN, STAFF, VIEWER, MEMBER (write: ADMIN, STAFF, MEMBER) | Ticket list/create; MEMBER sees own tickets |
| GET | `/tickets/:ticketCode` | authenticated; MEMBER owner-only | Ticket details and status history |
| PATCH, PUT | `/tickets/:ticketCode` | ADMIN, STAFF | Ticket status and admin fields |
| GET, POST | `/domains` | read: ADMIN, STAFF, VIEWER; write: ADMIN, STAFF | Domain list/create |
| GET, PATCH, PUT | `/domains/:id` | read: ADMIN, STAFF, VIEWER; write: ADMIN, STAFF | Domain details/update |
| POST | `/domains/:id/verify` | ADMIN | HTTPS Google verification meta-tag exact match; activates domain only on match |
| GET, POST | `/marketing-tags` | read: ADMIN, STAFF, VIEWER; write: ADMIN, STAFF | List/create tags |
| PATCH | `/marketing-tags/:id` | ADMIN, STAFF | Update tag |
| DELETE | `/marketing-tags/:id` | ADMIN | Soft-deactivate tag; no physical delete |
| GET | `/gsc/sites` | ADMIN | Read accessible Search Console properties |
| GET | `/gsc/sitemaps?siteUrl=...` | ADMIN | Read sitemap submissions at Google |
| POST | `/gsc/inspect` | ADMIN | Call URL Inspection API and record result |
| POST | `/gsc/sitemaps` | ADMIN | Submit sitemap through Search Console API and record result |
| GET | `/gsc/logs` | ADMIN, STAFF, VIEWER | Read D1 GSC history |
| GET | `/audit` | ADMIN | Read append-only audit log |
| GET, POST | `/users` | ADMIN | List/create Telegram user access rows |
| PATCH, DELETE | `/users/:id` | ADMIN | Update or deactivate a user |

List endpoints use `limit` (1–100, default 25) and `offset` (default 0). `q` and module-specific filters are optional.

## D1 migration

`migrations/0000_legacy_baseline.sql` is idempotent and mirrors the existing 38-column legacy schema. On an already populated database, the three `CREATE TABLE IF NOT EXISTS` statements and existing index declarations are no-ops; it does not rename or rewrite the legacy columns.

`migrations/0001_backoffice_api.sql` adds:

- `users` — Telegram account mapping and assignable role (`STAFF`, `VIEWER`, `MEMBER`)
- `marketing_tags` — tag configuration and enabled state
- `ticket_history` — append-only status changes, FK to `member_tickets.ticketCode` with RESTRICT
- `audit_logs` — append-only actor/action/resource log

Audit and ticket history tables have UPDATE/DELETE rejection triggers. The legacy tables are not altered by migration 0001.

Run local migration tests first:

```bash
cd cloudflare
npm test
npm run test:migrations
npx wrangler d1 migrations apply adminku-db --local
npx wrangler deploy --dry-run
```

Do not run `npm run migrations:remote` until after a production export/backup and separate explicit approval.

## Domain verification scope

`POST /domains/:id/verify` checks an HTTPS page under the stored domain and target path for a `<meta name="google-site-verification" content="...">` whose content exactly matches `verificationTag`. Only a match sets `status=ACTIVE` and `registrationStatus=APPROVED`. Redirects are limited to HTTPS on the same hostname; page reads are capped at 1 MiB. This verifies the configured meta-tag check only; it does **not** prove DNS ownership, SSL ownership, or Search Console account permission.

## Important functional limits

- Marketing Tags are stored and managed in D1. This API does not inject tags into customer websites; that requires a separate authorized deployment/snippet delivery mechanism.
- ReqWeb remains an external bot handoff until its owner supplies an official API/webhook and grants access.
- TrustPositif remains an external service link; this API does not claim to query or alter its blocklist.
- GSC URL Inspection reads Google’s indexed status. Sitemap submission is a registration request; neither action guarantees indexing or replaces a Google “Request Indexing” UI action.
- `/settings` is read-only. Runtime Variables, secrets, Pages URL, `TELEGRAM_WEBAPP_URL`, and `BASE_DOMAIN` are not modified by these endpoints.

## Deployment order (not performed by this overlay)

The new Worker code writes bot ticket events to `ticket_history` and `audit_logs`, so a future production release must apply the approved D1 migrations **before** deploying Worker version 2.2.0. Recommended release sequence: take and verify a D1 export/backup; review and apply `0000`/`0001` to the remote database with explicit approval; deploy the Worker; verify existing Telegram commands and `/api/v1/health`; only after Cloudflare Pages has a confirmed URL set `BACKOFFICE_ORIGIN`; test the Mini App end-to-end; then update `TELEGRAM_WEBAPP_URL` and BotFather configuration when the user approves. This source overlay performs none of those production actions.
