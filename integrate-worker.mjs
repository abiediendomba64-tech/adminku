#!/usr/bin/env node
/**
 * Patch the known Adminku Telegram Worker without rewriting its bot logic.
 * Stops when the source blob differs from the audited baseline.
 */
import { readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve } from "node:path";

const cliArgs = process.argv.slice(2);
const checkOnly = cliArgs.includes("--check-only");
const workerPath = resolve(cliArgs.find(arg => !arg.startsWith("--")) || "cloudflare/worker.js");
const EXPECTED_GIT_BLOB = "36e35a953fabfa3ad5008a148ddcf5fe0716b6fd";
const source = await readFile(workerPath, "utf8");
const gitBlob = createHash("sha1").update(`blob ${Buffer.byteLength(source)}\0`).update(source).digest("hex");
if (gitBlob !== EXPECTED_GIT_BLOB) {
  throw new Error(`Worker source changed: expected audited blob ${EXPECTED_GIT_BLOB}, found ${gitBlob}. Stop; review and rebase this patch manually.`);
}

let output = source;
function replaceExactlyOnce(needle, replacement, label) {
  const count = output.split(needle).length - 1;
  if (count !== 1) throw new Error(`${label}: expected one exact match, found ${count}. No file has been written.`);
  output = output.replace(needle, replacement);
}

replaceExactlyOnce(
  "*/\n\nexport default {",
  "*/\n\nimport { handleBackofficeApi } from \"./backoffice-api.js\";\n\nexport default {",
  "Worker API module import"
);
replaceExactlyOnce(
  "    const url = new URL(request.url);\n",
  "    const url = new URL(request.url);\n\n    // Backoffice REST API. All existing Telegram bot routes below remain unchanged.\n    if (url.pathname.startsWith(\"/api/\")) {\n      return handleBackofficeApi(request, env, ctx);\n    }\n",
  "REST API route dispatch"
);

const oldTicketInsert = [
  "        const inserted = await env.DB.prepare(",
  "          `INSERT OR IGNORE INTO member_tickets",
  "            (ticketCode, memberUsername, category, priority, domainAffected, issueDescription,",
  "             status, reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId, telegramChatId)",
  "           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`",
  "        ).bind(",
  "          candidate,",
  "          fromUsername.slice(0, 80),",
  "          \"Lainnya\",",
  "          \"TINGGI\",",
  "          \"\",",
  "          ticketIssue,",
  "          \"OPEN\",",
  "          \"TELEGRAM_BOT\",",
  "          now,",
  "          now,",
  "          \"\",",
  "          senderId,",
  "          chatId",
  "        ).run();"
].join("\n");
const newTicketInsert = [
  "        const insertStatement = env.DB.prepare(",
  "          `INSERT OR IGNORE INTO member_tickets",
  "            (ticketCode, memberUsername, category, priority, domainAffected, issueDescription,",
  "             status, reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId, telegramChatId)",
  "           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`",
  "        ).bind(",
  "          candidate, fromUsername.slice(0, 80), \"Lainnya\", \"TINGGI\", \"\", ticketIssue,",
  "          \"OPEN\", \"TELEGRAM_BOT\", now, now, \"\", senderId, chatId",
  "        );",
  "        // The dependent INSERT...SELECT statements only write audit rows when the ticket INSERT succeeded.",
  "        const historyStatement = env.DB.prepare(",
  "          `INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, changedByTelegramUserId, changedByUsername, source, createdAt)",
  "           SELECT ?, NULL, 'OPEN', 'Tiket dibuat melalui Telegram Bot', ?, ?, 'TELEGRAM_BOT', ? WHERE changes() > 0`",
  "        ).bind(candidate, senderId, fromUsername, now);",
  "        const auditStatement = env.DB.prepare(",
  "          `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)",
  "           SELECT ?, ?, ?, 'CREATE', 'ticket', ?, ?, ? WHERE changes() > 0`",
  "        ).bind(senderId, fromUsername, isAdmin ? \"ADMIN\" : \"MEMBER\", candidate, JSON.stringify({ status: \"OPEN\", reportedVia: \"TELEGRAM_BOT\" }), now);",
  "        const insertedBatch = await env.DB.batch([insertStatement, historyStatement, auditStatement]);"
].join("\n");
replaceExactlyOnce(oldTicketInsert, newTicketInsert, "Telegram /ticket transactional audit");
replaceExactlyOnce(
  "        if (inserted.meta?.changes > 0) {",
  "        if (insertedBatch[0]?.meta?.changes > 0) {",
  "Telegram /ticket inserted result"
);

const oldStatusUpdate = [
  "        await env.DB.prepare(",
  "          \"UPDATE member_tickets SET status = ?, lastUpdated = ? WHERE ticketCode = ?\"",
  "        ).bind(requestedStatus, Date.now(), ticketId).run();",
  "        displayStatus = requestedStatus;"
].join("\n");
const newStatusUpdate = [
  "        if (requestedStatus !== ticket.status) {",
  "          const changedAt = Date.now();",
  "          const updateStatement = env.DB.prepare(",
  "            \"UPDATE member_tickets SET status = ?, lastUpdated = ? WHERE ticketCode = ? AND status = ?\"",
  "          ).bind(requestedStatus, changedAt, ticketId, ticket.status);",
  "          const auditStatement = env.DB.prepare(",
  "            `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)",
  "             SELECT ?, ?, 'ADMIN', 'UPDATE_STATUS', 'ticket', ?, ?, ? WHERE changes() > 0`",
  "          ).bind(senderId, fromUsername, ticketId, JSON.stringify({ oldStatus: ticket.status, newStatus: requestedStatus }), changedAt);",
  "          const historyStatement = env.DB.prepare(",
  "            `INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, changedByTelegramUserId, changedByUsername, source, createdAt)",
  "             SELECT ?, ?, ?, 'Perubahan status melalui Telegram Bot', ?, ?, 'TELEGRAM_BOT', ? WHERE changes() > 0 AND ? <> ?`",
  "          ).bind(ticketId, ticket.status, requestedStatus, senderId, fromUsername, changedAt, ticket.status, requestedStatus);",
  "          const results = await env.DB.batch([updateStatement, auditStatement, historyStatement]);",
  "          if (!results[0]?.meta || results[0].meta.changes === 0) {",
  "            await sendTelegramMessage(botToken, chatId, \"⚠️ Status tiket berubah bersamaan. Periksa ulang dengan /status_ticket.\");",
  "            return;",
  "          }",
  "        }",
  "        displayStatus = requestedStatus;"
].join("\n");
replaceExactlyOnce(oldStatusUpdate, newStatusUpdate, "Telegram /status_ticket audit and history");

replaceExactlyOnce(
  "        });\n\n        await sendTelegramMessage(botToken, chatId,\n          \"🔎 <b>HASIL URL INSPECTION GOOGLE</b>\\n\" +",
  "        });\n        await recordBotAudit(env, senderId, fromUsername, \"INSPECT\", \"gsc_url\", trackingId, { inspectionUrl, siteUrl, verdict: indexStatus.verdict || \"UNKNOWN\" });\n\n        await sendTelegramMessage(botToken, chatId,\n          \"🔎 <b>HASIL URL INSPECTION GOOGLE</b>\\n\" +",
  "Telegram GSC inspection audit"
);
replaceExactlyOnce(
  "        });\n        await sendTelegramMessage(botToken, chatId,\n          \"✅ <b>SITEMAP DITERIMA API GOOGLE</b>\\n\" +",
  "        });\n        await recordBotAudit(env, senderId, fromUsername, \"SUBMIT_SITEMAP\", \"gsc_sitemap\", trackingId, { siteUrl, sitemapUrl, apiHttpStatus: apiResult.status });\n        await sendTelegramMessage(botToken, chatId,\n          \"✅ <b>SITEMAP DITERIMA API GOOGLE</b>\\n\" +",
  "Telegram GSC sitemap audit"
);

const helperAnchor = "async function getGoogleAccessToken(env) {";
const auditHelper = [
  "async function recordBotAudit(env, telegramUserId, username, action, resourceType, resourceId, details = {}) {",
  "  if (!env.DB) throw new Error(\"Binding D1 DB belum tersedia untuk audit.\");",
  "  await env.DB.prepare(",
  "    `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)",
  "     VALUES (?, ?, 'ADMIN', ?, ?, ?, ?, ?)`",
  "  ).bind(telegramUserId, username, action, resourceType, String(resourceId), JSON.stringify(details).slice(0, 8000), Date.now()).run();",
  "}",
  "",
  helperAnchor
].join("\n");
replaceExactlyOnce(helperAnchor, auditHelper, "Bot audit helper");
replaceExactlyOnce('version: "2.1.0"', 'version: "2.2.0"', "Worker version label");

if (checkOnly) {
  console.log(`Preflight OK for ${workerPath}; all integration anchors match audited blob ${gitBlob}. No file written.`);
} else {
  await writeFile(workerPath, output, "utf8");
  console.log(`Patched ${workerPath}. Git blob ${gitBlob} -> ${createHash("sha1").update(`blob ${Buffer.byteLength(output)}\0`).update(output).digest("hex")}`);
}
