# Adminku REST API + D1 Overlay

This is a source overlay for `abiediendomba64-tech/adminku`, based on the inspected `main` Worker blob `36e35a953fabfa3ad5008a148ddcf5fe0716b6fd`.

## Contents

- `cloudflare/backoffice-api.js` — REST API implementation (`/api/v1/*`)
- `cloudflare/migrations/` — additive D1 migrations
- `cloudflare/tests/` — API security tests and SQLite migration smoke test
- `scripts/integrate-worker.mjs` — exact-version checked patcher; `scripts/install-overlay.sh` — guarded local installer
- `backoffice-panel/` — static Web Panel source
- `cloudflare/README_BACKOFFICE_API.md` — route and setup reference

## Apply to a clean clone

The installer is designed for a clean clone whose `cloudflare/worker.js` still matches the audited source. Run:

```bash
git switch -c feat/backoffice-rest-api-v1
bash scripts/install-overlay.sh "$PWD"
```

It refuses a dirty working tree, existing conflicting overlay files, or a Worker whose audited source blob does not match. It makes a backup of `cloudflare/worker.js` before patching. The patcher changes the Worker only in the local Git working tree; no cloud API or deployment is called.

## Local validation

```bash
cd cloudflare
npm run check
npm test
npm run test:migrations
npx wrangler d1 migrations apply adminku-db --local
npx wrangler deploy --dry-run
```

The migration command uses `--local`; do not remove that flag for the first test. Do not apply remote migrations or deploy until after D1 backup and explicit approval.

## Production controls

This overlay does not apply remote D1 migrations, deploy the Worker, deploy Pages, set Runtime Variables, change bot menus, or modify secrets. Leave `BACKOFFICE_ORIGIN` unset until the exact Pages deployment origin exists. Do not change `TELEGRAM_WEBAPP_URL` or `BASE_DOMAIN` during this API phase.

After local tests pass, commit the overlay in the feature branch and open a PR to `main`. Keep it unmerged until the CI and code review pass.

## Web Panel

The static source is under `backoffice-panel/`. Its API calls use `/api/v1/*` and it requires Telegram Mini App `initData`; opening the Pages URL as an ordinary unauthenticated browser session is not a supported login flow.