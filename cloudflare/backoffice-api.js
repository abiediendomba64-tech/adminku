/**
 * Adminku Backoffice REST API (Cloudflare Workers + D1)
 * Existing Telegram bot routes stay in worker.js; this module owns /api/v1/*.
 * Authentication: Telegram Mini App raw initData, validated on every request.
 */

const API_PREFIX = "/api/v1";
const ROLE_VALUES = new Set(["ADMIN", "STAFF", "VIEWER", "MEMBER"]);
const TICKET_STATUSES = new Set(["OPEN", "DIPROSES", "SELESAI", "DITOLAK"]);
const TICKET_PRIORITIES = new Set(["RENDAH", "SEDANG", "TINGGI", "URGENT"]);
const DOMAIN_STATUSES = new Set(["PENDING_DNS", "PENDING", "ACTIVE", "SUSPENDED", "REJECTED"]);
const REGISTRATION_STATUSES = new Set(["PENDING", "APPROVED", "REJECTED"]);
const GSC_STATUSES = new Set(["SUBMITTED", "SITEMAP_SUBMITTED", "INDEXED", "NOT_INDEXED", "UNKNOWN"]);
const TAG_PLACEMENTS = new Set(["HEAD", "BODY_START", "BODY_END"]);

export class ApiError extends Error {
  constructor(status, message, code = "API_ERROR") {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function handleBackofficeApi(request, env) {
  const url = new URL(request.url);
  const origin = request.headers.get("Origin") || "";
  const allowedOrigins = parseAllowedOrigins(env.BACKOFFICE_ORIGIN);
  const isAllowedOrigin = !origin || allowedOrigins.includes(origin);

  if (request.method === "OPTIONS") {
    if (!origin || !allowedOrigins.includes(origin)) {
      return json({ error: "Origin tidak diizinkan.", code: "CORS_DENIED" }, 403);
    }
    return new Response(null, {
      status: 204,
      headers: corsHeaders(origin, true)
    });
  }

  if (!env.BACKOFFICE_ORIGIN || allowedOrigins.length === 0) {
    return json({ error: "BACKOFFICE_ORIGIN belum dikonfigurasi.", code: "API_NOT_CONFIGURED" }, 503);
  }
  if (!isAllowedOrigin) {
    return json({ error: "Origin tidak diizinkan.", code: "CORS_DENIED" }, 403);
  }

  if (!url.pathname.startsWith(API_PREFIX)) {
    return json({ error: "Endpoint tidak ditemukan.", code: "NOT_FOUND" }, 404, corsHeaders(origin));
  }
  if (url.pathname === `${API_PREFIX}/health` && request.method === "GET") {
    try {
      if (!env.DB) throw new Error("D1 binding tidak tersedia");
      await env.DB.prepare("SELECT 1 AS ok").first();
      return json({ status: "ok", service: "adminku-backoffice-api", timestamp: Date.now() }, 200, corsHeaders(origin));
    } catch {
      return json({ status: "degraded", service: "adminku-backoffice-api" }, 503, corsHeaders(origin));
    }
  }

  if (!env.DB) {
    return json({ error: "Binding D1 DB tidak tersedia.", code: "D1_UNAVAILABLE" }, 503, corsHeaders(origin));
  }

  try {
    const actor = await authenticateTelegramRequest(request, env);
    const response = await dispatchApi(request, url, env, actor);
    return withCors(response, origin);
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 500;
    const code = error instanceof ApiError ? error.code : "INTERNAL_ERROR";
    if (!(error instanceof ApiError)) console.error("Backoffice API failed:", error?.message || error);
    return json({ error: status === 500 ? "Terjadi kesalahan internal API." : error.message, code }, status, corsHeaders(origin));
  }
}

async function dispatchApi(request, url, env, actor) {
  const path = url.pathname.slice(API_PREFIX.length) || "/";
  const method = request.method.toUpperCase();

  if (path === "/auth/me" && method === "GET") {
    return json({ user: actor, auth: { provider: "telegram-mini-app", verified: true } });
  }

  if (path === "/settings" && method === "GET") {
    requireRole(actor, ["ADMIN", "STAFF", "VIEWER"]);
    return json({ data: {
      baseDomain: String(env.BASE_DOMAIN || "").trim(),
      webAppUrl: String(env.TELEGRAM_WEBAPP_URL || "").trim(),
      reqWebBotUrl: String(env.REQWEB_BOT_URL || "https://t.me/reqweb_bot").trim(),
      trustPositifUrl: "https://trustpositif.app/",
      backofficeOriginConfigured: parseAllowedOrigins(env.BACKOFFICE_ORIGIN).length > 0,
      initDataMaxAgeSeconds: parseMaxAge(env.INIT_DATA_MAX_AGE_SECONDS),
      note: "Perubahan Runtime Variables dan Secrets dilakukan melalui pengaturan Cloudflare, bukan dari endpoint panel."
    } });
  }

  if (path === "/dashboard" && method === "GET") {
    requireRole(actor, ["ADMIN", "STAFF", "VIEWER"]);
    const [ticketTotals, domainTotals, tagTotals, gscTotals, recentTickets, recentGsc] = await Promise.all([
      env.DB.prepare(`SELECT COUNT(*) AS total, SUM(CASE WHEN status='OPEN' THEN 1 ELSE 0 END) AS open,
        SUM(CASE WHEN status='DIPROSES' THEN 1 ELSE 0 END) AS inProgress,
        SUM(CASE WHEN status='SELESAI' THEN 1 ELSE 0 END) AS completed FROM member_tickets`).first(),
      env.DB.prepare(`SELECT COUNT(*) AS total, SUM(CASE WHEN status='ACTIVE' THEN 1 ELSE 0 END) AS active,
        SUM(CASE WHEN status IN ('PENDING','PENDING_DNS') THEN 1 ELSE 0 END) AS pending FROM domain_requests`).first(),
      env.DB.prepare("SELECT COUNT(*) AS total, SUM(CASE WHEN isActive=1 THEN 1 ELSE 0 END) AS active FROM marketing_tags").first(),
      env.DB.prepare("SELECT COUNT(*) AS total FROM gsc_submission_logs").first(),
      env.DB.prepare(`SELECT ticketCode, memberUsername, category, priority, status, createdAt, lastUpdated
        FROM member_tickets ORDER BY createdAt DESC LIMIT 8`).all(),
      env.DB.prepare(`SELECT id, url, domain, indexingStatus, gscTrackingId, submittedAt
        FROM gsc_submission_logs ORDER BY submittedAt DESC LIMIT 5`).all()
    ]);
    return json({
      generatedAt: Date.now(),
      tickets: normalizeCounts(ticketTotals),
      domains: normalizeCounts(domainTotals),
      marketingTags: normalizeCounts(tagTotals),
      gsc: { total: Number(gscTotals?.total || 0) },
      recentTickets: recentTickets.results || [],
      recentGsc: recentGsc.results || []
    });
  }

  if (path === "/tickets" && method === "GET") return listTickets(url, env, actor);
  if (path === "/tickets" && method === "POST") return createTicket(request, env, actor);
  const ticketMatch = path.match(/^\/tickets\/([A-Za-z0-9-]{3,64})$/);
  if (ticketMatch && method === "GET") return getTicket(ticketMatch[1], env, actor);
  if (ticketMatch && (method === "PATCH" || method === "PUT")) return updateTicket(ticketMatch[1], request, env, actor);

  if (path === "/domains" && method === "GET") return listDomains(url, env, actor);
  if (path === "/domains" && method === "POST") return createDomain(request, env, actor);
  const domainMatch = path.match(/^\/domains\/(\d+)$/);
  if (domainMatch && method === "GET") return getDomain(Number(domainMatch[1]), env, actor);
  if (domainMatch && (method === "PATCH" || method === "PUT")) return updateDomain(Number(domainMatch[1]), request, env, actor);
  const domainVerifyMatch = path.match(/^\/domains\/(\d+)\/verify$/);
  if (domainVerifyMatch && method === "POST") return verifyDomain(Number(domainVerifyMatch[1]), env, actor);

  if (path === "/marketing-tags" && method === "GET") return listTags(url, env, actor);
  if (path === "/marketing-tags" && method === "POST") return createTag(request, env, actor);
  const tagMatch = path.match(/^\/marketing-tags\/(\d+)$/);
  if (tagMatch && method === "PATCH") return updateTag(Number(tagMatch[1]), request, env, actor);
  if (tagMatch && method === "DELETE") return deactivateTag(Number(tagMatch[1]), env, actor);

  if (path === "/gsc/sites" && method === "GET") return listGscSites(env, actor);
  if (path === "/gsc/sitemaps" && method === "GET") return listGscSitemaps(url, env, actor);
  if (path === "/gsc/inspect" && method === "POST") return inspectGscUrl(request, env, actor);
  if (path === "/gsc/sitemaps" && method === "POST") return submitGscSitemap(request, env, actor);
  if (path === "/gsc/logs" && method === "GET") return listGscLogs(url, env, actor);

  if (path === "/audit" && method === "GET") return listAuditLogs(url, env, actor);
  if (path === "/users" && method === "GET") return listUsers(url, env, actor);
  if (path === "/users" && method === "POST") return createUser(request, env, actor);
  const userMatch = path.match(/^\/users\/(\d+)$/);
  if (userMatch && method === "PATCH") return updateUser(Number(userMatch[1]), request, env, actor);
  if (userMatch && method === "DELETE") return deactivateUser(Number(userMatch[1]), env, actor);

  return json({ error: "Endpoint tidak ditemukan.", code: "NOT_FOUND" }, 404);
}

// ---------- Authentication and authorization ----------

export async function authenticateTelegramInitData(rawInitData, env, nowSeconds = Math.floor(Date.now() / 1000)) {
  if (typeof rawInitData !== "string" || rawInitData.length < 20 || rawInitData.length > 8192) {
    throw new ApiError(401, "Telegram initData tidak tersedia atau ukurannya tidak valid.", "INVALID_INIT_DATA");
  }
  const botToken = String(env.TELEGRAM_BOT_TOKEN || "").trim();
  if (!botToken) throw new ApiError(503, "TELEGRAM_BOT_TOKEN belum dikonfigurasi.", "AUTH_NOT_CONFIGURED");

  const params = new URLSearchParams(rawInitData);
  const allKeys = [...params.keys()];
  if (new Set(allKeys).size !== allKeys.length) {
    throw new ApiError(401, "Telegram initData mengandung parameter duplikat.", "INVALID_INIT_DATA");
  }
  const receivedHash = params.get("hash") || "";
  if (!/^[a-f0-9]{64}$/i.test(receivedHash)) {
    throw new ApiError(401, "Hash Telegram tidak valid.", "INVALID_INIT_DATA");
  }
  const authDate = Number(params.get("auth_date"));
  const maxAge = parseMaxAge(env.INIT_DATA_MAX_AGE_SECONDS);
  if (!Number.isInteger(authDate) || authDate <= 0 || authDate > nowSeconds + 60 || nowSeconds - authDate > maxAge) {
    throw new ApiError(401, "Telegram initData sudah kedaluwarsa atau waktu tidak valid. Buka ulang Mini App.", "INIT_DATA_EXPIRED");
  }

  const dataCheckString = [...params.entries()]
    .filter(([key]) => key !== "hash")
    .map(([key, value]) => `${key}=${value}`)
    .sort()
    .join("\n");
  const encoder = new TextEncoder();
  const webAppKey = await crypto.subtle.importKey("raw", encoder.encode("WebAppData"), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const secretKeyBytes = await crypto.subtle.sign("HMAC", webAppKey, encoder.encode(botToken));
  const checkKey = await crypto.subtle.importKey("raw", secretKeyBytes, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", checkKey, encoder.encode(dataCheckString));
  const expectedHash = [...new Uint8Array(signature)].map(byte => byte.toString(16).padStart(2, "0")).join("");
  if (!constantTimeHexEqual(expectedHash, receivedHash.toLowerCase())) {
    throw new ApiError(401, "Validasi tanda tangan Telegram gagal.", "INVALID_INIT_DATA");
  }

  let telegramUser;
  try {
    telegramUser = JSON.parse(params.get("user") || "null");
  } catch {
    throw new ApiError(401, "Data pengguna Telegram tidak valid.", "INVALID_INIT_DATA");
  }
  if (!telegramUser || !Number.isSafeInteger(telegramUser.id) || telegramUser.id <= 0) {
    throw new ApiError(401, "Identitas pengguna Telegram tidak tersedia.", "INVALID_INIT_DATA");
  }
  return {
    telegramUserId: String(telegramUser.id),
    username: typeof telegramUser.username === "string" ? telegramUser.username.slice(0, 64) : "",
    firstName: typeof telegramUser.first_name === "string" ? telegramUser.first_name.slice(0, 120) : "",
    lastName: typeof telegramUser.last_name === "string" ? telegramUser.last_name.slice(0, 120) : "",
    languageCode: typeof telegramUser.language_code === "string" ? telegramUser.language_code.slice(0, 16) : "",
    authDate
  };
}

async function authenticateTelegramRequest(request, env) {
  const raw = request.headers.get("X-Telegram-Init-Data") || "";
  const telegram = await authenticateTelegramInitData(raw, env);
  const adminIds = getAdminIds(env);
  if (adminIds.has(telegram.telegramUserId)) {
    return { ...telegram, role: "ADMIN", isActive: true, source: "runtime_allowlist" };
  }
  const row = await env.DB.prepare(`SELECT id, telegramUserId, username, firstName, lastName, role, isActive
    FROM users WHERE telegramUserId = ? LIMIT 1`).bind(telegram.telegramUserId).first();
  if (!row || Number(row.isActive) !== 1) {
    throw new ApiError(403, "Akun Telegram belum diaktifkan oleh admin Adminku.", "USER_NOT_AUTHORIZED");
  }
  // A database row can never grant ADMIN. Admin access is controlled only by Runtime Variables.
  if (row.role === "ADMIN") {
    throw new ApiError(403, "Role ADMIN harus tercantum dalam TELEGRAM_ADMIN_ID / TELEGRAM_ADMIN_IDS.", "ADMIN_NOT_ALLOWLISTED");
  }
  return { ...telegram, id: row.id, role: ROLE_VALUES.has(row.role) ? row.role : "VIEWER", isActive: true, source: "users_table" };
}

function getAdminIds(env) {
  return new Set([env.TELEGRAM_ADMIN_ID || "", ...(env.TELEGRAM_ADMIN_IDS || "").split(",")]
    .map(value => String(value).trim()).filter(value => /^\d{3,20}$/.test(value)));
}
function parseMaxAge(value) {
  const parsed = Number(value || 3600);
  return Number.isInteger(parsed) && parsed >= 60 && parsed <= 86400 ? parsed : 3600;
}
function constantTimeHexEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
function requireRole(actor, roles) {
  if (!roles.includes(actor.role)) throw new ApiError(403, "Anda tidak memiliki hak akses untuk tindakan ini.", "FORBIDDEN");
}

// ---------- Tickets ----------

async function listTickets(url, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF", "VIEWER", "MEMBER"]);
  const { limit, offset } = getPaging(url);
  const status = url.searchParams.get("status");
  const q = (url.searchParams.get("q") || "").trim().slice(0, 120);
  const where = [];
  const binds = [];
  if (actor.role === "MEMBER") { where.push("telegramUserId = ?"); binds.push(actor.telegramUserId); }
  if (status) {
    if (!TICKET_STATUSES.has(status)) throw new ApiError(400, "Filter status tiket tidak valid.", "VALIDATION_ERROR");
    where.push("status = ?"); binds.push(status);
  }
  if (q) { where.push("(ticketCode LIKE ? OR memberUsername LIKE ? OR issueDescription LIKE ?)"); binds.push(`%${q}%`, `%${q}%`, `%${q}%`); }
  const clause = where.length ? `WHERE ${where.join(" AND ")}` : "";
  const count = await env.DB.prepare(`SELECT COUNT(*) AS total FROM member_tickets ${clause}`).bind(...binds).first();
  const result = await env.DB.prepare(`SELECT id, ticketCode, memberUsername, category, priority, domainAffected,
    issueDescription, status, reportedVia, createdAt, lastUpdated, adminNotes
    FROM member_tickets ${clause} ORDER BY createdAt DESC LIMIT ? OFFSET ?`).bind(...binds, limit, offset).all();
  return json({ data: result.results || [], pagination: pagination(limit, offset, count?.total || 0) });
}

async function getTicket(ticketCode, env, actor) {
  const row = await env.DB.prepare(`SELECT id, ticketCode, memberUsername, category, priority, domainAffected,
    issueDescription, status, reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId
    FROM member_tickets WHERE ticketCode = ? LIMIT 1`).bind(ticketCode.toUpperCase()).first();
  if (!row) throw new ApiError(404, "Tiket tidak ditemukan.", "NOT_FOUND");
  if (actor.role === "MEMBER" && row.telegramUserId !== actor.telegramUserId) throw new ApiError(404, "Tiket tidak ditemukan.", "NOT_FOUND");
  const { telegramUserId: ownerTelegramUserId, ...publicTicket } = row;
  const history = actor.role === "MEMBER"
    ? await env.DB.prepare(`SELECT id, oldStatus, newStatus, note, changedByUsername, source, createdAt
        FROM ticket_history WHERE ticketCode = ? ORDER BY createdAt DESC LIMIT 100`).bind(row.ticketCode).all()
    : await env.DB.prepare(`SELECT id, oldStatus, newStatus, note, changedByTelegramUserId, changedByUsername, source, createdAt
        FROM ticket_history WHERE ticketCode = ? ORDER BY createdAt DESC LIMIT 100`).bind(row.ticketCode).all();
  return json({ data: publicTicket, history: history.results || [] });
}

async function createTicket(request, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF", "MEMBER"]);
  const body = await readJson(request);
  const issueDescription = requiredString(body.issueDescription || body.issue, "Deskripsi kendala", 2000);
  const category = optionalString(body.category, "Lainnya", 80);
  const priority = String(body.priority || "SEDANG").toUpperCase();
  if (!TICKET_PRIORITIES.has(priority)) throw new ApiError(400, "Prioritas tiket tidak valid.", "VALIDATION_ERROR");
  const domainAffected = body.domainAffected ? normalizeDomain(body.domainAffected) : "";
  const now = Date.now();
  const ticketCode = `TKT-${now.toString(36).toUpperCase()}-${crypto.randomUUID().slice(0, 6).toUpperCase()}`;
  const username = actor.username ? `@${actor.username}` : (actor.firstName || "Telegram User");
  const statements = [
    env.DB.prepare(`INSERT INTO member_tickets (ticketCode, memberUsername, category, priority, domainAffected,
      issueDescription, status, reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId, telegramChatId)
      VALUES (?, ?, ?, ?, ?, ?, 'OPEN', 'WEB_APP', ?, ?, '', ?, '')`)
      .bind(ticketCode, username.slice(0, 80), category, priority, domainAffected, issueDescription, now, now, actor.telegramUserId),
    env.DB.prepare(`INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, changedByTelegramUserId,
      changedByUsername, source, createdAt) VALUES (?, NULL, 'OPEN', 'Tiket dibuat melalui Web App', ?, ?, 'WEB_APP', ?)`)
      .bind(ticketCode, actor.telegramUserId, username, now),
    makeAuditStatement(env.DB, actor, "CREATE", "ticket", ticketCode, { status: "OPEN", category, priority, reportedVia: "WEB_APP" }, now)
  ];
  await env.DB.batch(statements);
  return json({ data: { ticketCode, status: "OPEN", createdAt: now } }, 201);
}

async function updateTicket(ticketCode, request, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF"]);
  const body = await readJson(request);
  const code = ticketCode.toUpperCase();
  const current = await env.DB.prepare("SELECT ticketCode, status, priority, category, adminNotes, domainAffected FROM member_tickets WHERE ticketCode = ? LIMIT 1").bind(code).first();
  if (!current) throw new ApiError(404, "Tiket tidak ditemukan.", "NOT_FOUND");
  const status = body.status === undefined ? current.status : String(body.status).toUpperCase();
  const priority = body.priority === undefined ? current.priority : String(body.priority).toUpperCase();
  if (!TICKET_STATUSES.has(status) || !TICKET_PRIORITIES.has(priority)) throw new ApiError(400, "Status atau prioritas tiket tidak valid.", "VALIDATION_ERROR");
  const category = body.category === undefined ? current.category : requiredString(body.category, "Kategori", 80);
  const adminNotes = body.adminNotes === undefined ? current.adminNotes : optionalString(body.adminNotes, "", 2000);
  const domainAffected = body.domainAffected === undefined ? current.domainAffected : (body.domainAffected ? normalizeDomain(body.domainAffected) : "");
  const now = Date.now();
  const username = actor.username ? `@${actor.username}` : actor.firstName || "Telegram User";
  const changed = status !== current.status;
  const update = env.DB.prepare(`UPDATE member_tickets SET status = ?, priority = ?, category = ?, adminNotes = ?,
    domainAffected = ?, lastUpdated = ? WHERE ticketCode = ? AND status = ?`)
    .bind(status, priority, category, adminNotes, domainAffected, now, code, current.status);
  const audit = env.DB.prepare(`INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType,
    resourceId, detailsJson, createdAt) SELECT ?, ?, ?, 'UPDATE', 'ticket', ?, ?, ? WHERE changes() > 0`)
    .bind(actor.telegramUserId, username, actor.role, code, JSON.stringify({ status, priority, category, statusChanged: changed }).slice(0, 8000), now);
  const history = env.DB.prepare(`INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note,
    changedByTelegramUserId, changedByUsername, source, createdAt)
    SELECT ?, ?, ?, ?, ?, ?, 'WEB_APP', ? WHERE changes() > 0 AND ? <> ?`)
    .bind(code, current.status, status, optionalString(body.note, "Perubahan status melalui Web App", 1000), actor.telegramUserId, username, now, current.status, status);
  const batchResult = await env.DB.batch([update, audit, history]);
  if (!batchResult[0]?.meta || batchResult[0].meta.changes === 0) throw new ApiError(409, "Tiket berubah bersamaan. Muat ulang data lalu coba lagi.", "CONFLICT");
  return json({ data: { ticketCode: code, status, priority, category, lastUpdated: now } });
}

// Shared helpers are exported for Telegram bot writes, keeping its actions in the same audit trail.
export function ticketHistoryStatement(db, entry) {
  return db.prepare(`INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, changedByTelegramUserId,
    changedByUsername, source, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
    .bind(entry.ticketCode, entry.oldStatus ?? null, entry.newStatus, entry.note || "", entry.telegramUserId || "",
      entry.username || "Telegram Bot", entry.source || "TELEGRAM_BOT", entry.createdAt || Date.now());
}
export function auditLogStatement(db, actor, action, resourceType, resourceId, details = {}, createdAt = Date.now()) {
  return makeAuditStatement(db, actor, action, resourceType, resourceId, details, createdAt);
}

// ---------- Domains ----------

async function listDomains(url, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF", "VIEWER"]);
  const { limit, offset } = getPaging(url);
  const status = url.searchParams.get("status");
  const q = (url.searchParams.get("q") || "").trim().slice(0, 120);
  const where = [];
  const binds = [];
  if (status) { where.push("status = ?"); binds.push(status); }
  if (q) { where.push("domainName LIKE ?"); binds.push(`%${q}%`); }
  const clause = where.length ? `WHERE ${where.join(" AND ")}` : "";
  const count = await env.DB.prepare(`SELECT COUNT(*) AS total FROM domain_requests ${clause}`).bind(...binds).first();
  const rows = await env.DB.prepare(`SELECT id, domainName, category, targetPath, status, dnsSettings, notes,
    gscIndexingStatus, lastReindexedAt, verificationTag, registrationStatus, rejectionReason, createdAt
    FROM domain_requests ${clause} ORDER BY createdAt DESC LIMIT ? OFFSET ?`).bind(...binds, limit, offset).all();
  return json({ data: rows.results || [], pagination: pagination(limit, offset, count?.total || 0) });
}

async function getDomain(id, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF", "VIEWER"]);
  const row = await env.DB.prepare(`SELECT id, domainName, category, targetPath, status, dnsSettings, notes,
    gscIndexingStatus, lastReindexedAt, verificationTag, registrationStatus, rejectionReason, createdAt
    FROM domain_requests WHERE id = ? LIMIT 1`).bind(id).first();
  if (!row) throw new ApiError(404, "Domain tidak ditemukan.", "NOT_FOUND");
  return json({ data: row });
}

async function createDomain(request, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF"]);
  const body = await readJson(request);
  const domainName = normalizeDomain(body.domainName);
  const category = requiredString(body.category || "Umum", "Kategori", 80);
  const targetPath = validateTargetPath(body.targetPath || "/backoffice");
  const existing = await env.DB.prepare("SELECT id FROM domain_requests WHERE lower(domainName) = ? LIMIT 1").bind(domainName).first();
  if (existing) throw new ApiError(409, "Domain sudah terdaftar.", "DUPLICATE_DOMAIN");
  const now = Date.now();
  const notes = optionalString(body.notes, "", 2000);
  const insert = env.DB.prepare(`INSERT INTO domain_requests (domainName, category, targetPath, status, dnsSettings,
    notes, gscIndexingStatus, lastReindexedAt, verificationTag, registrationStatus, rejectionReason, createdAt)
    VALUES (?, ?, ?, 'PENDING_DNS', 'Cloudflare Proxy', ?, 'SUBMITTED', NULL, '', 'PENDING', '', ?)`)
    .bind(domainName, category, targetPath, notes, now);
  const audit = makeAuditStatement(env.DB, actor, "CREATE", "domain", domainName, { domainName, category, status: "PENDING_DNS" }, now);
  const results = await env.DB.batch([insert, audit]);
  const id = results[0]?.meta?.last_row_id;
  return json({ data: { id, domainName, status: "PENDING_DNS", createdAt: now } }, 201);
}

async function updateDomain(id, request, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF"]);
  const current = await env.DB.prepare("SELECT * FROM domain_requests WHERE id = ? LIMIT 1").bind(id).first();
  if (!current) throw new ApiError(404, "Domain tidak ditemukan.", "NOT_FOUND");
  const body = await readJson(request);
  const allowed = ["domainName", "category", "targetPath", "status", "dnsSettings", "notes", "gscIndexingStatus", "verificationTag", "registrationStatus", "rejectionReason"];
  const values = {};
  for (const key of allowed) if (body[key] !== undefined) values[key] = body[key];
  if (!Object.keys(values).length) throw new ApiError(400, "Tidak ada field yang dapat diperbarui.", "VALIDATION_ERROR");
  if (values.domainName !== undefined) values.domainName = normalizeDomain(values.domainName);
  if (values.category !== undefined) values.category = requiredString(values.category, "Kategori", 80);
  if (values.targetPath !== undefined) values.targetPath = validateTargetPath(values.targetPath);
  if (values.status !== undefined) { values.status = String(values.status).toUpperCase(); if (!DOMAIN_STATUSES.has(values.status)) throw new ApiError(400, "Status domain tidak valid.", "VALIDATION_ERROR"); if (values.status === "ACTIVE") throw new ApiError(400, "Domain harus melalui endpoint /verify sebelum diaktifkan.", "VERIFICATION_REQUIRED"); }
  if (values.registrationStatus !== undefined) { values.registrationStatus = String(values.registrationStatus).toUpperCase(); if (!REGISTRATION_STATUSES.has(values.registrationStatus)) throw new ApiError(400, "Status registrasi tidak valid.", "VALIDATION_ERROR"); if (values.registrationStatus === "APPROVED") throw new ApiError(400, "Persetujuan registrasi dilakukan melalui endpoint /verify.", "VERIFICATION_REQUIRED"); }
  if (values.gscIndexingStatus !== undefined) { values.gscIndexingStatus = String(values.gscIndexingStatus).toUpperCase(); if (!GSC_STATUSES.has(values.gscIndexingStatus)) throw new ApiError(400, "Status GSC tidak valid.", "VALIDATION_ERROR"); }
  for (const key of ["dnsSettings", "notes", "verificationTag", "rejectionReason"]) if (values[key] !== undefined) values[key] = optionalString(values[key], "", key === "notes" ? 2000 : 1000);
  if (values.domainName && values.domainName !== current.domainName) {
    const duplicate = await env.DB.prepare("SELECT id FROM domain_requests WHERE lower(domainName) = ? AND id <> ? LIMIT 1").bind(values.domainName, id).first();
    if (duplicate) throw new ApiError(409, "Domain sudah digunakan oleh data lain.", "DUPLICATE_DOMAIN");
  }
  const assignments = Object.keys(values).map(key => `${key} = ?`);
  const update = env.DB.prepare(`UPDATE domain_requests SET ${assignments.join(", ")} WHERE id = ?`)
    .bind(...Object.values(values), id);
  const audit = makeConditionalAuditStatement(env.DB, actor, "UPDATE", "domain", id, values);
  const results = await env.DB.batch([update, audit]);
  if (!results[0]?.meta || results[0].meta.changes === 0) throw new ApiError(409, "Domain tidak berubah atau diperbarui bersamaan.", "CONFLICT");
  return json({ data: { id, ...values } });
}

async function verifyDomain(id, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const current = await env.DB.prepare("SELECT id, domainName, targetPath, verificationTag, status FROM domain_requests WHERE id=? LIMIT 1").bind(id).first();
  if (!current) throw new ApiError(404, "Domain tidak ditemukan.", "NOT_FOUND");
  const expectedTag = String(current.verificationTag || "").trim();
  if (!expectedTag) throw new ApiError(409, "Verification Tag belum diisi pada data domain.", "VERIFICATION_TAG_REQUIRED");

  const target = new URL(validateTargetPath(current.targetPath || "/"), `https://${current.domainName}`);
  let response;
  let requestUrl = target;
  try {
    // Follow at most three same-host HTTPS redirects. Cross-host redirects are rejected.
    for (let hop = 0; hop <= 3; hop += 1) {
      response = await fetch(requestUrl.toString(), { redirect: "manual", headers: { "User-Agent": "Adminku-Domain-Verification/1.0" } });
      if (response.status < 300 || response.status >= 400) break;
      const location = response.headers.get("Location");
      if (!location || hop === 3) break;
      const next = new URL(location, requestUrl);
      if (next.protocol !== "https:" || next.hostname !== target.hostname || next.port) {
        throw new ApiError(502, "Redirect domain verification keluar dari host HTTPS yang diizinkan.", "VERIFICATION_REDIRECT_BLOCKED");
      }
      requestUrl = next;
    }
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(502, "Tidak dapat mengambil halaman domain untuk verifikasi.", "DOMAIN_FETCH_FAILED");
  }

  const html = await readLimitedResponseText(response, 1024 * 1024);
  const foundTag = extractMetaVerificationTag(html);
  const verified = response.ok && foundTag === expectedTag;
  const now = Date.now();
  const newStatus = verified ? "ACTIVE" : current.status;
  const audit = makeAuditStatement(env.DB, actor, verified ? "VERIFY_PASS" : "VERIFY_FAIL", "domain", id,
    { domainName: current.domainName, requestUrl: requestUrl.toString(), httpStatus: response.status, verificationTagFound: Boolean(foundTag), matched: verified }, now);
  if (verified) {
    const update = env.DB.prepare("UPDATE domain_requests SET status='ACTIVE', registrationStatus='APPROVED' WHERE id=? AND status<> 'ACTIVE'").bind(id);
    await env.DB.batch([update, audit]);
  } else {
    await audit.run();
  }
  return json({ data: { id, domainName: current.domainName, verified, httpStatus: response.status,
    verificationTagFound: Boolean(foundTag), status: newStatus, checkedAt: now,
    method: "HTTPS meta tag exact-match; this does not verify DNS ownership or Google Search Console permission." } });
}

async function readLimitedResponseText(response, maxBytes) {
  if (!response?.body) return "";
  const declaredLength = Number(response.headers.get("Content-Length") || 0);
  if (declaredLength > maxBytes) throw new ApiError(413, "Halaman domain terlalu besar untuk pemeriksaan tag (batas 1 MiB).", "PAGE_TOO_LARGE");
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) {
        await reader.cancel();
        throw new ApiError(413, "Halaman domain terlalu besar untuk pemeriksaan tag (batas 1 MiB).", "PAGE_TOO_LARGE");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const merged = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { merged.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder().decode(merged);
}

function extractMetaVerificationTag(html) {
  const tags = String(html || "").match(/<meta\b[^>]*>/gi) || [];
  for (const tag of tags) {
    const name = htmlTagAttribute(tag, "name");
    if (name?.toLowerCase() !== "google-site-verification") continue;
    return htmlTagAttribute(tag, "content") || "";
  }
  return "";
}

function htmlTagAttribute(tag, attribute) {
  const escaped = attribute.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`(?:^|\\s)${escaped}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`, "i");
  const match = String(tag).match(pattern);
  return match ? (match[1] ?? match[2] ?? match[3] ?? "").trim() : "";
}

// ---------- Marketing tags ----------

async function listTags(url, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF", "VIEWER"]);
  const { limit, offset } = getPaging(url);
  const active = url.searchParams.get("active");
  const q = (url.searchParams.get("q") || "").trim().slice(0, 120);
  const where = [];
  const binds = [];
  if (active === "true" || active === "false") { where.push("isActive = ?"); binds.push(active === "true" ? 1 : 0); }
  if (q) { where.push("(name LIKE ? OR tagType LIKE ? OR tagId LIKE ? OR targetDomain LIKE ?)"); binds.push(`%${q}%`, `%${q}%`, `%${q}%`, `%${q}%`); }
  const clause = where.length ? `WHERE ${where.join(" AND ")}` : "";
  const count = await env.DB.prepare(`SELECT COUNT(*) AS total FROM marketing_tags ${clause}`).bind(...binds).first();
  const rows = await env.DB.prepare(`SELECT id, name, tagType, tagId, targetDomain, targetPath, placement, isActive,
    notes, createdAt, updatedAt, createdBy FROM marketing_tags ${clause} ORDER BY createdAt DESC LIMIT ? OFFSET ?`)
    .bind(...binds, limit, offset).all();
  return json({ data: rows.results || [], pagination: pagination(limit, offset, count?.total || 0) });
}

async function createTag(request, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF"]);
  const body = await readJson(request);
  const values = validateTagBody(body, false);
  const now = Date.now();
  const insert = env.DB.prepare(`INSERT INTO marketing_tags (name, tagType, tagId, targetDomain, targetPath,
    placement, isActive, notes, createdAt, updatedAt, createdBy)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
    .bind(values.name, values.tagType, values.tagId, values.targetDomain, values.targetPath, values.placement,
      values.isActive, values.notes, now, now, actor.telegramUserId);
  const audit = makeAuditStatement(env.DB, actor, "CREATE", "marketing_tag", values.tagId, { name: values.name, tagType: values.tagType, isActive: values.isActive }, now);
  const results = await env.DB.batch([insert, audit]);
  const id = results[0]?.meta?.last_row_id;
  return json({ data: { id, ...values, createdAt: now, updatedAt: now } }, 201);
}

async function updateTag(id, request, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF"]);
  const current = await env.DB.prepare("SELECT * FROM marketing_tags WHERE id = ? LIMIT 1").bind(id).first();
  if (!current) throw new ApiError(404, "Marketing tag tidak ditemukan.", "NOT_FOUND");
  const body = await readJson(request);
  const merged = { ...current, ...body };
  const values = validateTagBody(merged, true);
  const now = Date.now();
  const update = env.DB.prepare(`UPDATE marketing_tags SET name=?, tagType=?, tagId=?, targetDomain=?, targetPath=?,
    placement=?, isActive=?, notes=?, updatedAt=? WHERE id=?`)
    .bind(values.name, values.tagType, values.tagId, values.targetDomain, values.targetPath, values.placement,
      values.isActive, values.notes, now, id);
  const audit = makeConditionalAuditStatement(env.DB, actor, "UPDATE", "marketing_tag", id, { name: values.name, tagType: values.tagType, isActive: values.isActive }, now);
  const results = await env.DB.batch([update, audit]);
  if (!results[0]?.meta || results[0].meta.changes === 0) throw new ApiError(409, "Tag tidak berubah atau diperbarui bersamaan.", "CONFLICT");
  return json({ data: { id, ...values, updatedAt: now } });
}

async function deactivateTag(id, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const now = Date.now();
  const update = env.DB.prepare("UPDATE marketing_tags SET isActive=0, updatedAt=? WHERE id=? AND isActive=1").bind(now, id);
  const audit = env.DB.prepare(`INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)
    SELECT ?, ?, ?, 'DEACTIVATE', 'marketing_tag', ?, ?, ? WHERE changes() > 0`)
    .bind(...auditValues(actor, "DEACTIVATE", "marketing_tag", id, { isActive: false }, now));
  const results = await env.DB.batch([update, audit]);
  if (!results[0]?.meta || results[0].meta.changes === 0) throw new ApiError(404, "Tag tidak ditemukan atau sudah nonaktif.", "NOT_FOUND");
  return json({ data: { id, isActive: false } });
}

function validateTagBody(body, allowActive) {
  const name = requiredString(body.name, "Nama tag", 120);
  const tagType = requiredString(body.tagType, "Jenis tag", 40).toUpperCase();
  if (!/^[A-Z0-9_-]{2,40}$/.test(tagType)) throw new ApiError(400, "Jenis tag hanya boleh berisi huruf, angka, underscore, dan tanda hubung.", "VALIDATION_ERROR");
  const tagId = requiredString(body.tagId, "ID tag", 128);
  if (!/^[A-Za-z0-9._:-]{2,128}$/.test(tagId)) throw new ApiError(400, "Format ID tag tidak valid.", "VALIDATION_ERROR");
  const targetDomain = body.targetDomain && body.targetDomain !== "*" ? normalizeDomain(body.targetDomain) : "*";
  const targetPath = validateTargetPath(body.targetPath || "/");
  const placement = String(body.placement || "HEAD").toUpperCase();
  if (!TAG_PLACEMENTS.has(placement)) throw new ApiError(400, "Penempatan tag tidak valid.", "VALIDATION_ERROR");
  const isActive = allowActive ? (body.isActive === false || body.isActive === 0 ? 0 : 1) : 1;
  return { name, tagType, tagId, targetDomain, targetPath, placement, isActive, notes: optionalString(body.notes, "", 2000) };
}

// ---------- Google Search Console ----------

async function listGscSites(env, actor) {
  requireRole(actor, ["ADMIN"]);
  const accessToken = await getGoogleAccessToken(env);
  const result = await googleRequest(accessToken, "GET", "https://www.googleapis.com/webmasters/v3/sites");
  return json({ data: result.data.siteEntry || [], httpStatus: result.status });
}

async function listGscSitemaps(url, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const siteUrl = normalizeGscSiteProperty(url.searchParams.get("siteUrl"));
  const accessToken = await getGoogleAccessToken(env);
  const result = await googleRequest(accessToken, "GET", `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(siteUrl)}/sitemaps`);
  return json({ siteUrl, data: result.data.sitemap || [], httpStatus: result.status });
}

async function inspectGscUrl(request, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const body = await readJson(request);
  const inspectionUrl = normalizeHttpUrl(body.inspectionUrl);
  const siteUrl = normalizeGscSiteProperty(body.siteUrl || `${new URL(inspectionUrl).origin}/`);
  if (!isUrlWithinGscProperty(inspectionUrl, siteUrl)) throw new ApiError(400, "URL tidak berada di dalam properti Search Console yang dipilih.", "GSC_PROPERTY_MISMATCH");
  const accessToken = await getGoogleAccessToken(env);
  const apiResult = await googleRequest(accessToken, "POST", "https://searchconsole.googleapis.com/v1/urlInspection/index:inspect",
    { inspectionUrl, siteUrl, languageCode: "id-ID" });
  const inspection = apiResult.data.inspectionResult || {};
  const indexStatus = inspection.indexStatusResult || {};
  const publicSignals = await inspectPublicPage(siteUrl.startsWith("sc-domain:") ? `https://${siteUrl.slice(10)}/` : siteUrl);
  const sitemapResult = await googleRequest(accessToken, "GET", `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(siteUrl)}/sitemaps`).catch(() => ({ data: { sitemap: [] } }));
  const trackingId = createGscTrackingId();
  const diagnostic = {
    source: "Google Search Console URL Inspection API", apiHttpStatus: apiResult.status,
    inspectionUrl, siteUrl, verdict: indexStatus.verdict || "UNKNOWN", coverageState: indexStatus.coverageState || "",
    indexingState: indexStatus.indexingState || "", pageFetchState: indexStatus.pageFetchState || "",
    robotsTxtState: indexStatus.robotsTxtState || "", lastCrawlTime: indexStatus.lastCrawlTime || null,
    googleCanonical: indexStatus.googleCanonical || "", userCanonical: indexStatus.userCanonical || "",
    sitemapCount: (sitemapResult.data.sitemap || []).length, publicPageHttpStatus: publicSignals.httpStatus,
    verificationTagFound: publicSignals.verificationTagFound
  };
  await env.DB.batch([
    gscLogStatement(env.DB, { url: inspectionUrl, domain: getGscPropertyDomain(siteUrl), httpStatus: publicSignals.httpStatus,
      verificationTagFound: publicSignals.verificationTagFound, sitemapDetected: (sitemapResult.data.sitemap || []).length > 0,
      indexingStatus: indexStatus.verdict || "UNKNOWN", diagnosticMessage: JSON.stringify(diagnostic), gscTrackingId: trackingId }),
    makeAuditStatement(env.DB, actor, "INSPECT", "gsc_url", trackingId, { inspectionUrl, siteUrl, verdict: indexStatus.verdict || "UNKNOWN" })
  ]);
  return json({ data: { trackingId, ...diagnostic } });
}

async function submitGscSitemap(request, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const body = await readJson(request);
  const siteUrl = normalizeGscSiteProperty(body.siteUrl);
  const sitemapUrl = normalizeHttpUrl(body.sitemapUrl);
  if (!isUrlWithinGscProperty(sitemapUrl, siteUrl)) throw new ApiError(400, "URL sitemap tidak berada di dalam properti Search Console yang dipilih.", "GSC_PROPERTY_MISMATCH");
  const accessToken = await getGoogleAccessToken(env);
  const apiResult = await googleRequest(accessToken, "PUT", `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(siteUrl)}/sitemaps/${encodeURIComponent(sitemapUrl)}`);
  const publicSignals = await inspectPublicPage(siteUrl.startsWith("sc-domain:") ? `https://${siteUrl.slice(10)}/` : siteUrl);
  const trackingId = createGscTrackingId();
  const diagnostic = { source: "Google Search Console Sitemaps API", apiHttpStatus: apiResult.status, siteUrl, sitemapUrl,
    acceptedByGoogleApi: true, note: "Penerimaan sitemap bukan jaminan URL di-crawl atau diindeks." };
  await env.DB.batch([
    gscLogStatement(env.DB, { url: sitemapUrl, domain: getGscPropertyDomain(siteUrl), httpStatus: publicSignals.httpStatus || apiResult.status,
      verificationTagFound: publicSignals.verificationTagFound, sitemapDetected: true, indexingStatus: "SITEMAP_SUBMITTED",
      diagnosticMessage: JSON.stringify(diagnostic), gscTrackingId: trackingId }),
    makeAuditStatement(env.DB, actor, "SUBMIT_SITEMAP", "gsc_sitemap", trackingId, { siteUrl, sitemapUrl, apiHttpStatus: apiResult.status })
  ]);
  return json({ data: { trackingId, ...diagnostic } }, 201);
}

async function listGscLogs(url, env, actor) {
  requireRole(actor, ["ADMIN", "STAFF", "VIEWER"]);
  const { limit, offset } = getPaging(url);
  const domain = (url.searchParams.get("domain") || "").trim().slice(0, 253);
  const where = domain ? "WHERE domain = ?" : "";
  const binds = domain ? [domain] : [];
  const count = await env.DB.prepare(`SELECT COUNT(*) AS total FROM gsc_submission_logs ${where}`).bind(...binds).first();
  const rows = await env.DB.prepare(`SELECT id, url, domain, searchEngine, httpStatus, verificationTagFound, sitemapDetected,
    indexingStatus, diagnosticMessage, gscTrackingId, submittedAt FROM gsc_submission_logs ${where}
    ORDER BY submittedAt DESC LIMIT ? OFFSET ?`).bind(...binds, limit, offset).all();
  return json({ data: rows.results || [], pagination: pagination(limit, offset, count?.total || 0) });
}

async function getGoogleAccessToken(env) {
  const clientId = String(env.GSC_CLIENT_ID || "").trim();
  const clientSecret = String(env.GSC_CLIENT_SECRET || "").trim();
  const refreshToken = String(env.GSC_REFRESH_TOKEN || "").trim();
  if (!clientId || !clientSecret || !refreshToken) throw new ApiError(503, "Kredensial OAuth GSC belum lengkap.", "GSC_NOT_CONFIGURED");
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: clientId, client_secret: clientSecret, refresh_token: refreshToken, grant_type: "refresh_token" })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.access_token) throw new ApiError(502, "Google OAuth gagal memperbarui token.", "GSC_OAUTH_FAILED");
  return data.access_token;
}
async function googleRequest(token, method, url, body) {
  const response = await fetch(url, { method, headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { "Content-Type": "application/json" }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const raw = await response.text();
  let data = {};
  if (raw) { try { data = JSON.parse(raw); } catch { data = {}; } }
  if (!response.ok) throw new ApiError(502, `Google Search Console API gagal (HTTP ${response.status}).`, "GSC_API_FAILED");
  return { status: response.status, data };
}
function gscLogStatement(db, entry) {
  return db.prepare(`INSERT INTO gsc_submission_logs (url, domain, searchEngine, httpStatus, verificationTagFound, sitemapDetected,
    indexingStatus, diagnosticMessage, gscTrackingId, submittedAt) VALUES (?, ?, 'Google Search Console API', ?, ?, ?, ?, ?, ?, ?)`)
    .bind(entry.url, entry.domain, Number.isInteger(entry.httpStatus) ? entry.httpStatus : 0,
      entry.verificationTagFound ? 1 : 0, entry.sitemapDetected ? 1 : 0, entry.indexingStatus,
      String(entry.diagnosticMessage || "").slice(0, 8000), entry.gscTrackingId, Date.now());
}
async function inspectPublicPage(url) {
  try {
    const response = await fetch(url, { redirect: "follow", headers: { "User-Agent": "Adminku-GSC-Verification-Check/1.0" } });
    const html = await response.text();
    const verificationTagFound = /<meta\b(?=[^>]*\bname\s*=\s*["']google-site-verification["'])[^>]*>/i.test(html);
    return { httpStatus: response.status, verificationTagFound };
  } catch { return { httpStatus: 0, verificationTagFound: false }; }
}
function normalizeGscSiteProperty(value) {
  const input = String(value || "").trim();
  if (/^sc-domain:/i.test(input)) {
    const domain = input.slice(10).toLowerCase();
    if (!isValidDomain(domain)) throw new ApiError(400, "Format Domain Property tidak valid.", "VALIDATION_ERROR");
    return `sc-domain:${domain}`;
  }
  const url = normalizeHttpUrl(input.includes("://") ? input : `https://${input}`);
  const parsed = new URL(url);
  if (parsed.search || parsed.hash) throw new ApiError(400, "Properti URL-prefix tidak boleh memiliki query atau fragment.", "VALIDATION_ERROR");
  if (!parsed.pathname.endsWith("/")) parsed.pathname += "/";
  return parsed.toString();
}
function normalizeHttpUrl(value) {
  let parsed;
  try { parsed = new URL(String(value || "").trim()); } catch { throw new ApiError(400, "URL tidak valid.", "VALIDATION_ERROR"); }
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || !isValidDomain(parsed.hostname)) {
    throw new ApiError(400, "URL harus berupa HTTP/HTTPS dan hostname yang valid.", "VALIDATION_ERROR");
  }
  return parsed.toString();
}
function isUrlWithinGscProperty(targetUrl, siteUrl) {
  const target = new URL(targetUrl);
  if (siteUrl.startsWith("sc-domain:")) {
    const domain = siteUrl.slice(10);
    return target.hostname === domain || target.hostname.endsWith(`.${domain}`);
  }
  const property = new URL(siteUrl);
  return target.protocol === property.protocol && target.hostname === property.hostname && target.port === property.port && target.pathname.startsWith(property.pathname);
}
function getGscPropertyDomain(siteUrl) { return siteUrl.startsWith("sc-domain:") ? siteUrl.slice(10) : new URL(siteUrl).hostname; }
function createGscTrackingId() { return `GSC-CF-${Date.now().toString(36).toUpperCase()}-${crypto.randomUUID().replace(/-/g, "").slice(0, 10).toUpperCase()}`; }

// ---------- Audit log and user management ----------

async function listAuditLogs(url, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const { limit, offset } = getPaging(url);
  const resourceType = (url.searchParams.get("resourceType") || "").trim().slice(0, 60);
  const where = resourceType ? "WHERE resourceType = ?" : "";
  const binds = resourceType ? [resourceType] : [];
  const count = await env.DB.prepare(`SELECT COUNT(*) AS total FROM audit_logs ${where}`).bind(...binds).first();
  const rows = await env.DB.prepare(`SELECT id, actorTelegramUserId, actorUsername, actorRole, action, resourceType,
    resourceId, detailsJson, createdAt FROM audit_logs ${where} ORDER BY createdAt DESC LIMIT ? OFFSET ?`).bind(...binds, limit, offset).all();
  return json({ data: rows.results || [], pagination: pagination(limit, offset, count?.total || 0) });
}

async function listUsers(url, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const { limit, offset } = getPaging(url);
  const rows = await env.DB.prepare(`SELECT id, telegramUserId, username, firstName, lastName, role, isActive, notes,
    createdAt, updatedAt, createdBy FROM users ORDER BY createdAt DESC LIMIT ? OFFSET ?`).bind(limit, offset).all();
  const count = await env.DB.prepare("SELECT COUNT(*) AS total FROM users").first();
  return json({ data: rows.results || [], runtimeAdminIds: [...getAdminIds(env)], pagination: pagination(limit, offset, count?.total || 0) });
}

async function createUser(request, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const body = await readJson(request);
  const telegramUserId = String(body.telegramUserId || "").trim();
  if (!/^\d{3,20}$/.test(telegramUserId)) throw new ApiError(400, "Telegram User ID harus berupa angka.", "VALIDATION_ERROR");
  if (getAdminIds(env).has(telegramUserId)) throw new ApiError(409, "ID ini sudah menjadi admin melalui Runtime Variables.", "DUPLICATE_USER");
  const role = String(body.role || "VIEWER").toUpperCase();
  if (!["STAFF", "VIEWER", "MEMBER"].includes(role)) throw new ApiError(400, "Role yang dapat diberikan dari panel: STAFF, VIEWER, atau MEMBER. ADMIN harus melalui Runtime Variables.", "VALIDATION_ERROR");
  const username = optionalString(body.username, "", 64).replace(/^@/, "");
  const firstName = optionalString(body.firstName, "", 120);
  const lastName = optionalString(body.lastName, "", 120);
  const notes = optionalString(body.notes, "", 1000);
  const now = Date.now();
  try {
    const insert = env.DB.prepare(`INSERT INTO users (telegramUserId, username, firstName, lastName, role, isActive, notes, createdAt, updatedAt, createdBy)
      VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?)`).bind(telegramUserId, username, firstName, lastName, role, notes, now, now, actor.telegramUserId);
    const audit = makeAuditStatement(env.DB, actor, "CREATE", "user", telegramUserId, { telegramUserId, role }, now);
    const results = await env.DB.batch([insert, audit]);
    const id = results[0]?.meta?.last_row_id;
    return json({ data: { id, telegramUserId, username, firstName, lastName, role, isActive: 1, createdAt: now } }, 201);
  } catch (error) {
    if (String(error?.message || "").includes("UNIQUE")) throw new ApiError(409, "Pengguna Telegram sudah terdaftar.", "DUPLICATE_USER");
    throw error;
  }
}

async function updateUser(id, request, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const current = await env.DB.prepare("SELECT * FROM users WHERE id = ? LIMIT 1").bind(id).first();
  if (!current) throw new ApiError(404, "Pengguna tidak ditemukan.", "NOT_FOUND");
  if (getAdminIds(env).has(String(current.telegramUserId))) throw new ApiError(409, "Akun admin Runtime Variables dikelola melalui konfigurasi Worker, bukan tabel users.", "RUNTIME_ADMIN_MANAGED");
  const body = await readJson(request);
  const role = body.role === undefined ? current.role : String(body.role).toUpperCase();
  if (!["STAFF", "VIEWER", "MEMBER"].includes(role)) throw new ApiError(400, "Role tidak valid. ADMIN hanya melalui Runtime Variables.", "VALIDATION_ERROR");
  const isActive = body.isActive === undefined ? Number(current.isActive) : (body.isActive === true || body.isActive === 1 ? 1 : 0);
  const notes = body.notes === undefined ? current.notes : optionalString(body.notes, "", 1000);
  const now = Date.now();
  const update = env.DB.prepare("UPDATE users SET role=?, isActive=?, notes=?, updatedAt=? WHERE id=?").bind(role, isActive, notes, now, id);
  const audit = makeAuditStatement(env.DB, actor, "UPDATE", "user", id, { role, isActive }, now);
  await env.DB.batch([update, audit]);
  return json({ data: { id, role, isActive, notes, updatedAt: now } });
}
async function deactivateUser(id, env, actor) {
  requireRole(actor, ["ADMIN"]);
  const current = await env.DB.prepare("SELECT telegramUserId FROM users WHERE id=? LIMIT 1").bind(id).first();
  if (!current) throw new ApiError(404, "Pengguna tidak ditemukan.", "NOT_FOUND");
  if (getAdminIds(env).has(String(current.telegramUserId))) throw new ApiError(409, "Akun admin Runtime Variables tidak dapat dinonaktifkan melalui panel.", "RUNTIME_ADMIN_MANAGED");
  const now = Date.now();
  const update = env.DB.prepare("UPDATE users SET isActive=0, updatedAt=? WHERE id=? AND isActive=1").bind(now, id);
  const audit = makeConditionalAuditStatement(env.DB, actor, "DEACTIVATE", "user", id, { isActive: false }, now);
  const results = await env.DB.batch([update, audit]);
  if (!results[0]?.meta || results[0].meta.changes === 0) throw new ApiError(409, "Pengguna sudah nonaktif atau berubah bersamaan.", "CONFLICT");
  return json({ data: { id, isActive: 0, updatedAt: now } });
}

// ---------- Utilities ----------

function parseAllowedOrigins(value) {
  return String(value || "").split(",").map(item => item.trim()).filter(Boolean).map(item => {
    try { const parsed = new URL(item); return parsed.origin === item.replace(/\/$/, "") ? parsed.origin : ""; } catch { return ""; }
  }).filter(Boolean);
}
function corsHeaders(origin, preflight = false) {
  const headers = new Headers({ "Vary": "Origin", "Cache-Control": "no-store" });
  if (origin) headers.set("Access-Control-Allow-Origin", origin);
  if (preflight) {
    headers.set("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE, OPTIONS");
    headers.set("Access-Control-Allow-Headers", "Content-Type, X-Telegram-Init-Data");
    headers.set("Access-Control-Max-Age", "600");
  }
  return headers;
}
function withCors(response, origin) {
  const headers = new Headers(response.headers);
  const cors = corsHeaders(origin);
  for (const [key, value] of cors.entries()) headers.set(key, value);
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}
function json(data, status = 200, headers = undefined) {
  const resultHeaders = new Headers(headers || {});
  resultHeaders.set("Content-Type", "application/json; charset=utf-8");
  resultHeaders.set("Cache-Control", "no-store");
  return new Response(JSON.stringify(data), { status, headers: resultHeaders });
}
async function readJson(request) {
  const text = await request.text();
  if (!text || text.length > 20000) throw new ApiError(400, "Body JSON kosong atau terlalu besar (maksimum 20 KB).", "INVALID_BODY");
  let value;
  try { value = JSON.parse(text); } catch { throw new ApiError(400, "Body harus berupa JSON yang valid.", "INVALID_BODY"); }
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new ApiError(400, "Body harus berupa objek JSON.", "INVALID_BODY");
  return value;
}
function requiredString(value, label, max) {
  if (typeof value !== "string" || !value.trim() || value.trim().length > max) throw new ApiError(400, `${label} wajib diisi (maksimum ${max} karakter).`, "VALIDATION_ERROR");
  return value.trim();
}
function optionalString(value, fallback, max) {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== "string" || value.length > max) throw new ApiError(400, `Nilai teks terlalu panjang (maksimum ${max} karakter).`, "VALIDATION_ERROR");
  return value.trim();
}
function normalizeDomain(value) {
  const domain = requiredString(value, "Nama domain", 253).toLowerCase().replace(/\.$/, "");
  if (!isValidDomain(domain)) throw new ApiError(400, "Format nama domain tidak valid.", "VALIDATION_ERROR");
  return domain;
}
function isValidDomain(value) {
  return typeof value === "string" && value.length <= 253 && /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$/i.test(value);
}
function validateTargetPath(value) {
  const path = requiredString(value, "Target path", 500);
  let decodedPath = path;
  try {
    // Decode repeatedly so double-encoded slash, backslash, dot-segment, or control bytes are not a bypass.
    for (let pass = 0; pass < 4; pass += 1) {
      const next = decodeURIComponent(decodedPath);
      if (next === decodedPath) break;
      decodedPath = next;
    }
  } catch {
    throw new ApiError(400, "Target path mengandung percent-encoding yang tidak valid.", "VALIDATION_ERROR");
  }
  if (!path.startsWith("/") || !decodedPath.startsWith("/") || path.startsWith("//") || decodedPath.startsWith("//") ||
      path.includes("\\") || decodedPath.includes("\\") || /[\u0000-\u001f\u007f]/.test(decodedPath) ||
      /(^|\/)\.\.?($|\/)/.test(decodedPath) || /[?#]/.test(decodedPath)) {
    throw new ApiError(400, "Target path harus berupa path lokal; URL absolut, //, backslash, query, fragment, control byte, dan dot-segment dilarang.", "VALIDATION_ERROR");
  }
  return path;
}
function getPaging(url) {
  const rawLimit = Number(url.searchParams.get("limit") || 25);
  const rawOffset = Number(url.searchParams.get("offset") || 0);
  if (!Number.isInteger(rawLimit) || rawLimit < 1 || rawLimit > 100 || !Number.isInteger(rawOffset) || rawOffset < 0 || rawOffset > 100000) {
    throw new ApiError(400, "Pagination tidak valid. Gunakan limit 1-100 dan offset 0-100000.", "VALIDATION_ERROR");
  }
  return { limit: rawLimit, offset: rawOffset };
}
function pagination(limit, offset, total) { return { limit, offset, total: Number(total || 0), hasMore: offset + limit < Number(total || 0) }; }
function normalizeCounts(row) { const result = {}; for (const [key, value] of Object.entries(row || {})) result[key] = Number(value || 0); return result; }
function auditSql() {
  return `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`;
}
function auditValues(actor, action, resourceType, resourceId, details = {}, createdAt = Date.now()) {
  return [actor.telegramUserId, actor.username || actor.firstName || "", actor.role, action, resourceType, String(resourceId ?? ""), JSON.stringify(details).slice(0, 8000), createdAt];
}
function auditStatementCompat(db, actor, action, resourceType, resourceId, details, createdAt) {
  return db.prepare(auditSql()).bind(...auditValues(actor, action, resourceType, resourceId, details, createdAt));
}

function makeAuditStatement(db, actor, action, resourceType, resourceId, details = {}, createdAt = Date.now()) {
  return auditStatementCompat(db, actor, action, resourceType, resourceId, details, createdAt);
}

function makeConditionalAuditStatement(db, actor, action, resourceType, resourceId, details = {}, createdAt = Date.now()) {
  return db.prepare(`INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)
    SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE changes() > 0`)
    .bind(...auditValues(actor, action, resourceType, resourceId, details, createdAt));
}

// Public, deterministic helpers for unit tests.
export const __test = {
  normalizeDomain,
  validateTargetPath,
  parseMaxAge,
  constantTimeHexEqual,
  parseAllowedOrigins,
  normalizeGscSiteProperty,
  normalizeHttpUrl,
  isUrlWithinGscProperty,
  extractMetaVerificationTag,
  htmlTagAttribute
};
