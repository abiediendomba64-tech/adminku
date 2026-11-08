#!/usr/bin/env bash
set -euo pipefail

OVERLAY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-}"
if [[ -z "$TARGET" ]]; then
  echo "Usage: bash install-overlay.sh /path/to/adminku-clone" >&2
  exit 2
fi
TARGET="$(cd "$TARGET" && pwd)"
if [[ ! -d "$TARGET/.git" || ! -f "$TARGET/cloudflare/worker.js" ]]; then
  echo "Target must be a Git clone containing cloudflare/worker.js." >&2
  exit 2
fi
if [[ -n "$(git -C "$TARGET" status --porcelain)" ]]; then
  echo "Refusing to install into a dirty working tree. Commit or stash changes first." >&2
  exit 2
fi
for path in cloudflare/backoffice-api.js cloudflare/migrations/0000_legacy_baseline.sql cloudflare/migrations/0001_backoffice_api.sql .github/workflows/cloudflare-backoffice-api-validation.yml backoffice-panel/index.html backoffice-panel/app.js backoffice-panel/config.js backoffice-panel/styles.css backoffice-panel/_headers; do
  if [[ -e "$TARGET/$path" ]]; then
    echo "Refusing to overwrite existing file: $path" >&2
    exit 2
  fi
done

# Validate exact Worker patch anchors before copying or editing anything.
node "$OVERLAY_DIR/scripts/integrate-worker.mjs" "$TARGET/cloudflare/worker.js" --check-only

mkdir -p "$TARGET/cloudflare/migrations" "$TARGET/cloudflare/tests" "$TARGET/scripts" "$TARGET/.github/workflows"
cp "$OVERLAY_DIR/cloudflare/backoffice-api.js" "$TARGET/cloudflare/backoffice-api.js"
cp "$OVERLAY_DIR/cloudflare/migrations/0000_legacy_baseline.sql" "$TARGET/cloudflare/migrations/0000_legacy_baseline.sql"
cp "$OVERLAY_DIR/cloudflare/migrations/0001_backoffice_api.sql" "$TARGET/cloudflare/migrations/0001_backoffice_api.sql"
cp "$OVERLAY_DIR/cloudflare/tests/backoffice-api.test.js" "$TARGET/cloudflare/tests/backoffice-api.test.js"
cp "$OVERLAY_DIR/cloudflare/tests/migration_smoke.py" "$TARGET/cloudflare/tests/migration_smoke.py"
cp "$OVERLAY_DIR/scripts/integrate-worker.mjs" "$TARGET/scripts/integrate-worker.mjs"
cp "$OVERLAY_DIR/cloudflare/README_BACKOFFICE_API.md" "$TARGET/cloudflare/README_BACKOFFICE_API.md"

node --input-type=module - "$TARGET/cloudflare/package.json" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises';
const path = process.argv[2];
const pkg = JSON.parse(await readFile(path, 'utf8'));
pkg.scripts = {
  ...(pkg.scripts || {}),
  check: 'node --check worker.js && node --check backoffice-api.js',
  test: 'node --test tests/*.test.js',
  'test:migrations': 'python3 tests/migration_smoke.py',
  'migrations:local': 'npx --yes wrangler@latest d1 migrations apply adminku-db --local'
};
await writeFile(path, JSON.stringify(pkg, null, 2) + '\n');
NODE

node --input-type=module - "$TARGET/cloudflare/wrangler.toml" <<'NODE'
import { readFile, writeFile } from 'node:fs/promises';
const path = process.argv[2];
let text = await readFile(path, 'utf8');
const matches = text.match(/\[\[d1_databases\]\]/g) || [];
if (matches.length !== 1) throw new Error(`Expected exactly one [[d1_databases]] block; found ${matches.length}. Review config manually.`);
const migrationLines = text.match(/^migrations_dir\s*=\s*"([^"]+)"\s*$/gm) || [];
if (migrationLines.length > 1) throw new Error('Multiple migrations_dir entries found; review config manually.');
if (migrationLines.length === 1 && migrationLines[0].match(/"([^"]+)"/)[1] !== 'migrations') {
  throw new Error('Existing migrations_dir is not "migrations"; refusing to overwrite.');
}
if (migrationLines.length === 0) {
  const marker = 'database_id = "70751701-1ff7-412c-acaa-1b8bb583fc67"';
  if (!text.includes(marker)) throw new Error('Expected Adminku D1 database_id was not found. Review config manually.');
  text = text.replace(marker, marker + '\nmigrations_dir = "migrations"');
}
await writeFile(path, text);
NODE

# Keep a recoverable local snapshot of the original Worker before applying the patch.
BACKUP="$(mktemp /tmp/adminku-worker-pre-backoffice.XXXXXX.js)"
cp "$TARGET/cloudflare/worker.js" "$BACKUP"
node "$TARGET/scripts/integrate-worker.mjs" "$TARGET/cloudflare/worker.js"
cp "$OVERLAY_DIR/.github/workflows/cloudflare-backoffice-api-validation.yml" "$TARGET/.github/workflows/cloudflare-backoffice-api-validation.yml"
mkdir -p "$TARGET/backoffice-panel"
cp "$OVERLAY_DIR/backoffice-panel/"* "$TARGET/backoffice-panel/"

echo "Overlay installed in local working tree. Original Worker backup: $BACKUP"
echo "No remote database, Worker, Pages, secrets, or Runtime Variables were changed."
echo "Next: cd cloudflare && npm run check && npm test && npm run test:migrations."
