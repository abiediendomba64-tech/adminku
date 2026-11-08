# Adminku REST API + D1 Overlay

This is a source overlay for `abiediendomba64-tech/adminku`, based on the inspected `main` Worker blob `36e35a953fabfa3ad5008a148ddcf5fe0716b6fd`.

## Contents

- `cloudflare/backoffice-api.js` — REST API implementation (`/api/v1/*`)
- `cloudflare/migrations/0000_legacy_baseline.sql` — safe, idempotent baseline for the existing 3 legacy tables
- `cloudflare/migrations/0001_backoffice_api.sql` — new users, marketing tags, ticket history, audit log tables and append-only triggers
- `cloudflare/tests/` — API security tests and SQLite migration smoke test
- `scripts/integrate-worker.mjs` — exact-version checked patcher that adds API routing and includes Telegram bot ticket/GSC writes in the shared audit trail
- `cloudflare/README_BACKOFFICE_API.md` — route and setup reference

## Apply to a clean clone

```bash
git clone https://github.com/abiediendomba64-tech/adminku.git
cd adminku
git switch -c feat/backoffice-rest-api-v1
```

Extract this overlay elsewhere, then install it:

```bash
bash /path/to/overlay/install-overlay.sh "$PWD"
```

The installer refuses a dirty working tree, existing conflicting overlay files, or a Worker whose audited source blob does not match. It makes a backup of `cloudflare/worker.js` before patching. The patcher changes the Worker only in the local Git working tree; no cloud API or deployment is called.

## Local validation after installation

```bash
cd cloudflare
npm run check
npm test
npm run test:migrations
npx wrangler d1 migrations apply adminku-db --local
npx wrangler deploy --dry-run
```

The Wrangler local migration command writes only Wrangler's local D1 state. The dry-run packages the Worker but does not deploy it.

## Production controls

This overlay does not apply remote D1 migrations, deploy the Worker, deploy Pages, set Runtime Variables, change bot menus, or modify secrets. Leave `BACKOFFICE_ORIGIN` unset until the exact Pages deployment origin exists. Do not change `TELEGRAM_WEBAPP_URL` or `BASE_DOMAIN` during this API phase.

After local tests pass, commit the overlay in the feature branch and open a PR to `main`. Keep it unmerged until the CI and code review pass.

## Web Panel

The static source is included under `backoffice-panel/`. Its API calls use `/api/v1/*` and its UI fields are aligned with the current API response contract (dashboard aggregates, `isActive` marketing-tag fields, and append-only audit-log fields). It requires Telegram Mini App `initData`; opening the Pages URL as an ordinary unauthenticated browser session is not a supported login flow.
