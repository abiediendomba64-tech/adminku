# Adminku REST API + D1 source overlay

This repository package implements the first backend phase for Adminku Backoffice. It is an **overlay**, not a replacement repository for the Android app, the Telegram bot, or the rest of the Adminku project.

## Included

- `/api/v1/*` REST API source with Telegram Mini App `initData` HMAC validation, CORS allowlist, role-based access, D1 parameterized queries, and append-only audit/history records.
- Additive D1 migrations: safe legacy baseline plus `users`, `marketing_tags`, `ticket_history`, and `audit_logs`.
- A guarded integration patch for the existing `cloudflare/worker.js` that keeps current Telegram routes/commands and adds API dispatch + audit writes from bot ticket/GSC actions.
- API tests, SQLite migration smoke tests, and a GitHub Actions workflow for local validation only.
- Installer to apply the overlay to a clean clone of `abiediendomba64-tech/adminku`.

## Not included / not claimed

- No Web App frontend in this phase.
- No Pages deployment, Worker deployment, remote D1 migration, Runtime Variable update, BotFather update, or Android APK release.
- Marketing Tags are stored as configuration; automatic injection into third-party websites is not implemented. ReqWeb and TrustPositif remain external links until official API access exists.

## Install into the main repo

```bash
git clone https://github.com/abiediendomba64-tech/adminku.git
cd adminku
git switch -c feat/backoffice-rest-api-v1
bash /path/to/adminku-rest-api-overlay/install-overlay.sh "$PWD"
```

The installer requires a clean working tree and the exact Worker source blob that was audited. It stops rather than applying an uncertain patch.

## Local tests

```bash
cd cloudflare
npm run check
npm test
npm run test:migrations
npx --yes wrangler@latest d1 migrations apply adminku-db --local
npx --yes wrangler@latest deploy --dry-run
```

The Wrangler migration command above uses `--local`; do not remove that flag for the first test. Do not apply remote migrations or deploy until after D1 backup and explicit approval.

See [`INTEGRATION.md`](INTEGRATION.md) and [`cloudflare/README_BACKOFFICE_API.md`](cloudflare/README_BACKOFFICE_API.md) for route details and deployment gates.
