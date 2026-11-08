/**
 * Cloudflare Worker for Telegram Bot & Backoffice Automation
 * Free Tier (100,000 requests/day, 0ms cold start, no server required)
 * 
 * Target Admin ID: 7862805424
 * Target Admin Handle: @felixsnd (https://t.me/felixsnd)
 * Target Telegram Group: https://t.me/+ybOzZ_lstEdhNDU1
 * Web App URL: https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app
 */

import { handleBackofficeApi } from "./backoffice-api.js";

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Backoffice REST API. All existing Telegram bot routes below remain unchanged.
    if (url.pathname.startsWith("/api/")) {
      return handleBackofficeApi(request, env, ctx);
    }

    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/status")) {
      let database = "unbound";
      if (env.DB) {
        try {
          await env.DB.prepare("SELECT 1 AS ok").first();
          database = "connected";
        } catch {
          database = "error";
        }
      }

      return jsonResponse({
        status: "online",
        service: "Backoffice Telegram Bot",
        version: "2.2.0",
        database,
        timestamp: new Date().toISOString()
      });
    }

    // Maintenance endpoints use a dedicated admin secret. The Telegram webhook secret
    // is reserved for authenticating incoming updates from Telegram.
    if (url.pathname === "/setup-webhook") {
      if (request.method !== "POST") {
        return new Response("Method Not Allowed", { status: 405, headers: { Allow: "POST" } });
      }

      const adminSecret = env.WEBHOOK_ADMIN_SECRET || "";
      const webhookSecret = env.TELEGRAM_WEBHOOK_SECRET || "";
      const token = env.TELEGRAM_BOT_TOKEN || "";
      if (!adminSecret || !webhookSecret || !token) {
        return jsonResponse({ error: "Runtime secrets WEBHOOK_ADMIN_SECRET, TELEGRAM_WEBHOOK_SECRET, and TELEGRAM_BOT_TOKEN must be configured." }, 503);
      }
      if (adminSecret === webhookSecret) {
        return jsonResponse({ error: "WEBHOOK_ADMIN_SECRET and TELEGRAM_WEBHOOK_SECRET must be different values." }, 503);
      }
      if (!isAuthorizedMaintenanceRequest(request, adminSecret)) {
        return jsonResponse({ error: "Unauthorized" }, 401);
      }

      const webhookUrl = new URL("/webhook", url.origin).toString();
      const telegramResponse = await fetch(`https://api.telegram.org/bot${token}/setWebhook`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          url: webhookUrl,
          secret_token: webhookSecret,
          allowed_updates: ["message"],
          drop_pending_updates: false
        })
      });
      const result = await telegramResponse.json();
      return jsonResponse({
        webhook_url: webhookUrl,
        telegram: result
      }, telegramResponse.ok && result.ok ? 200 : 502);
    }

    if (url.pathname === "/webhook-info") {
      if (request.method !== "GET") {
        return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET" } });
      }

      const adminSecret = env.WEBHOOK_ADMIN_SECRET || "";
      const webhookSecret = env.TELEGRAM_WEBHOOK_SECRET || "";
      const token = env.TELEGRAM_BOT_TOKEN || "";
      if (!adminSecret || !token) {
        return jsonResponse({ error: "Runtime secrets WEBHOOK_ADMIN_SECRET and TELEGRAM_BOT_TOKEN must be configured." }, 503);
      }
      if (webhookSecret && adminSecret === webhookSecret) {
        return jsonResponse({ error: "WEBHOOK_ADMIN_SECRET and TELEGRAM_WEBHOOK_SECRET must be different values." }, 503);
      }
      if (!isAuthorizedMaintenanceRequest(request, adminSecret)) {
        return jsonResponse({ error: "Unauthorized" }, 401);
      }

      const telegramResponse = await fetch(`https://api.telegram.org/bot${token}/getWebhookInfo`);
      const result = await telegramResponse.json();
      return jsonResponse(result, telegramResponse.ok && result.ok ? 200 : 502);
    }

    if (url.pathname === "/webhook") {
      if (request.method !== "POST") {
        return new Response("Method Not Allowed", { status: 405, headers: { Allow: "POST" } });
      }

      const botToken = env.TELEGRAM_BOT_TOKEN || "";
      const webhookSecret = env.TELEGRAM_WEBHOOK_SECRET || "";
      if (!botToken || !webhookSecret) {
        return new Response("Webhook secrets are not configured", { status: 503 });
      }

      const receivedSecret = request.headers.get("X-Telegram-Bot-Api-Secret-Token") || "";
      if (!timingSafeEqual(receivedSecret, webhookSecret)) {
        return new Response("Unauthorized", { status: 401 });
      }

      let update;
      try {
        update = await request.json();
      } catch {
        return new Response("Invalid JSON", { status: 400 });
      }

      ctx.waitUntil(
        handleTelegramUpdate(update, botToken, env, url.origin)
          .catch((error) => console.error("Telegram update processing failed:", error))
      );
      return new Response("OK", { status: 200 });
    }

    return new Response("Not Found", { status: 404 });
  }
};

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}

function isAuthorizedMaintenanceRequest(request, secret) {
  const authorization = request.headers.get("Authorization") || "";
  return timingSafeEqual(authorization, `Bearer ${secret}`);
}

function timingSafeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string") return false;
  let mismatch = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i += 1) {
    mismatch |= (left.charCodeAt(i) || 0) ^ (right.charCodeAt(i) || 0);
  }
  return mismatch === 0;
}

/**
 * Admin authorization uses numeric Telegram user IDs, not usernames.
 * TELEGRAM_ADMIN_ID remains supported for backwards compatibility.
 * TELEGRAM_ADMIN_IDS accepts comma-separated additional admin IDs.
 */
function getAdminIds(env) {
  const configuredIds = [
    env.TELEGRAM_ADMIN_ID || "7862805424",
    ...(env.TELEGRAM_ADMIN_IDS || "").split(",")
  ];
  return new Set(
    configuredIds
      .map((value) => String(value).trim())
      .filter((value) => /^\d+$/.test(value))
  );
}

/**
 * Handle incoming Telegram Update
 */
async function handleTelegramUpdate(update, botToken, env, workerOrigin) {
  if (!update || !update.message) return;

  const msg = update.message;
  const chatId = msg.chat?.id ? String(msg.chat.id) : "";
  const text = (msg.text || "").trim();
  const fromUsername = msg.from?.username ? `@${msg.from.username}` : (msg.from?.first_name || "User");
  const isGroup = (msg.chat?.type || "").includes("group");

  if (!chatId || !text) return;

  const adminIds = getAdminIds(env);
  const adminId = env.TELEGRAM_ADMIN_ID || "7862805424";
  const groupLink = env.TELEGRAM_GROUP_LINK || "https://t.me/+ybOzZ_lstEdhNDU1";
  const webAppUrl = env.TELEGRAM_WEBAPP_URL || "https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app";
  const configuredReqWebBotUrl = (env.REQWEB_BOT_URL || "https://t.me/reqweb_bot").trim();
  const reqWebBotUrl = /^https:\/\/t\.me\/[A-Za-z0-9_]+\/?$/.test(configuredReqWebBotUrl)
    ? configuredReqWebBotUrl
    : "https://t.me/reqweb_bot";
  const configuredBaseDomain = (env.BASE_DOMAIN || "").trim().toLowerCase();
  const baseDomain = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$/i.test(configuredBaseDomain) ? configuredBaseDomain : "";

  const senderId = msg.from?.id ? String(msg.from.id) : "";
  const isAdmin = adminIds.has(senderId);

  // Parse command & arguments
  const parts = text.split(/\s+/);
  const command = parts[0].toLowerCase().split("@")[0]; // remove @botname if in group
  const args = parts.slice(1);

  switch (command) {
    case "/start": {
      const welcomeText = `👋 <b>Halo, ${fromUsername}!</b>\n\n` +
        `Selamat datang di <b>Backoffice Bot Suite & GSC Automation</b>.\n` +
        `Sistem ini berjalan di <b>Cloudflare Workers 24/7 Serverless (Free Tier)</b>.\n\n` +
        `🛠 <b>Perintah Tersedia:</b>\n` +
        `• <code>/panel</code> — Link akses /backoffice & marketing tags\n` +
        `• <code>/reindex &lt;domain&gt;</code> — Trigger re-indexing GSC & audit domain\n` +
        `• <code>/ticket &lt;kendala&gt;</code> — Buat tiket keluhan member (Auto-assign ID #TKT)\n` +
        `• <code>/status_ticket &lt;id&gt; &lt;status&gt;</code> — Cek/update status tiket\n` +
        `• <code>/reqweb [rincian]</code> — Buka layanan eksternal @reqweb_bot untuk permintaan web\n` +
        `• <code>/gsc_inspect &lt;domain&gt;</code> — Inspeksi Google Search Console\n` +
        `• <code>/trustpositif &lt;domain&gt;</code> — Cek database blokir Kominfo\n` +
        `• <code>/list_domains</code> — Daftar domain aktif\n` +
        `• <code>/report</code> — Ringkasan sistem & tiket ke admin\n\n` +
        (isAdmin ? `

<b>Google Search Console (Admin):</b>
          • <code>/gsc_test</code> — Tes koneksi API
          • <code>/gsc_sites</code> — Daftar properti
          • <code>/gsc_inspect &lt;URL&gt; [properti]</code> — Periksa status indeks
          • <code>/gsc_sitemaps &lt;properti&gt;</code> — Daftar sitemap
          • <code>/gsc_submit_sitemap &lt;properti&gt; &lt;URL&gt;</code> — Kirim sitemap
` : ``) +
        `Klik tombol di bawah untuk membuka Web App interaktif:`;

      await sendTelegramMessage(botToken, chatId, welcomeText, {
        inline_keyboard: [
          [{ text: "🌐 Buka Panel Web App", web_app: { url: webAppUrl } }],
          [{ text: "🛠️ Layanan ReqWeb", url: reqWebBotUrl }],
          [{ text: "💬 Hubungi Admin (@felixsnd)", url: "https://t.me/felixsnd" }]
        ]
      });
      break;
    }

    case "/reqweb": {
      const requestDetails = args.join(" ").trim().slice(0, 1200);
      const reqWebText = requestDetails
        ? "🛠️ <b>LAYANAN PERMINTAAN WEB — REQWEB</b>\n" +
          "Rincian yang Anda tulis:\n<pre>" + escapeHtml(requestDetails) + "</pre>\n" +
          "Buka bot ReqWeb, lalu kirim rincian tersebut langsung di percakapan ReqWeb.\n\n" +
          "<i>Catatan: Adminku hanya membuka tautan ke bot eksternal. Pengiriman permintaan dan status pengerjaan belum tersinkron otomatis ke database Adminku.</i>"
        : "🛠️ <b>LAYANAN PERMINTAAN WEB — REQWEB</b>\n" +
          "Gunakan bot ReqWeb untuk mengirim permintaan web/website.\n\n" +
          "Anda juga dapat menambahkan rincian setelah perintah, misalnya:\n" +
          "<code>/reqweb Buat landing page untuk domain contoh.com</code>\n\n" +
          "<i>Adminku membuka layanan eksternal; rincian harus Anda kirim sendiri ke ReqWeb.</i>\n" +
          "Untuk mencatat kendala secara internal, gunakan <code>/ticket [rincian]</code> di chat Adminku.";

      await sendTelegramMessage(botToken, chatId, reqWebText, {
        inline_keyboard: [
          [{ text: "🛠️ Buka @reqweb_bot", url: reqWebBotUrl }]
        ]
      });
      break;
    }

    case "/panel": {
      if (!baseDomain) {
        await sendTelegramMessage(botToken, chatId, "⚠️ Domain panel belum dikonfigurasi. Admin harus mengisi BASE_DOMAIN dengan domain yang valid di Runtime Variables.");
        return;
      }

      const panelUrl = `https://${baseDomain}/backoffice`;
      const tagsUrl = `https://${baseDomain}/backoffice/settings/marketing-tags`;
      const gscUrl = "https://search.google.com/search-console/welcome";
      const panelText = `🌐 <b>Panel Backoffice & Marketing Settings</b>\n\n` +
        `• <b>Domain Utama:</b> <code>${escapeHtml(baseDomain)}</code>\n` +
        `• <b>Dashboard:</b> <a href="${panelUrl}">${panelUrl}</a>\n` +
        `• <b>Marketing Tags:</b> <a href="${tagsUrl}">${tagsUrl}</a>\n` +
        `• <b>Google Search Console:</b> <a href="${gscUrl}">${gscUrl}</a>`;

      await sendTelegramMessage(botToken, chatId, panelText, {
        inline_keyboard: [
          [{ text: "🚀 Luncurkan Web App Backoffice", web_app: { url: webAppUrl } }],
          [{ text: "🏷️ Buka Marketing Tags", url: tagsUrl }]
        ]
      });
      break;
    }

    case "/reindex": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Audit domain hanya tersedia untuk admin.");
        return;
      }
      const targetDomain = args[0] ? args[0].replace(/^https?:\/\//, "").replace(/\/.*$/, "").toLowerCase() : baseDomain;
      if (!isValidDomainName(targetDomain)) {
        await sendTelegramMessage(botToken, chatId, "Format: /reindex domainanda.com. Masukkan domain yang valid.");
        return;
      }
      const trackingCode = `GSC-CF-${Math.floor(100000 + Math.random() * 900000)}`;

      // Audit domain via live fetch
      let httpStatus = "Unknown";
      let metaTagFound = false;
      let sitemapFound = false;

      try {
        const resp = await fetch(`https://${targetDomain}`, {
          headers: { "User-Agent": "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)" },
          redirect: "follow"
        });
        httpStatus = `${resp.status} ${resp.statusText}`;
        const html = await resp.text();
        metaTagFound = html.includes("google-site-verification");
        
        // Check sitemap
        const sitemapResp = await fetch(`https://${targetDomain}/sitemap.xml`);
        sitemapFound = sitemapResp.ok;
      } catch (err) {
        httpStatus = `Error: ${err.message}`;
      }

      const reportText = `⚡ <b>[AUDIT DOMAIN PRA-GSC]</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>Domain:</b> <code>${targetDomain}</code>\n` +
        `• <b>Referensi Audit Lokal:</b> <code>#${trackingCode}</code>\n` +
        `• <b>HTTP Status:</b> <code>${httpStatus}</code>\n` +
        `• <b>Meta Verification:</b> ${metaTagFound ? "✅ TERDETEKSI" : "⚠️ BELUM TERDETEKSI"}\n` +
        `• <b>Sitemap.xml:</b> ${sitemapFound ? "✅ VALID" : "❌ TIDAK DITEMUKAN"}\n` +
        `• <b>Status API GSC:</b> TIDAK ADA PERMINTAAN INDEXING YANG DIKIRIM\n` +
        `• <b>Dipicu oleh:</b> ${fromUsername} (Cloudflare Worker 24/7)\n` +
        `• <b>Waktu:</b> ${new Date().toISOString()}\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `<i>Ini hanya audit HTTP/meta/sitemap. Tidak ada permintaan indexing yang dikirim ke Google. Gunakan /gsc_inspect dan /gsc_submit_sitemap.</i>`;

      // 1. Reply to sender
      await sendTelegramMessage(botToken, chatId, reportText, {
        inline_keyboard: [
          [{ text: "🔍 Inspeksi GSC", url: `https://search.google.com/search-console/inspect?resource_id=sc-domain:${targetDomain}` }],
          [{ text: "🛡️ Cek TrustPositif", url: "https://trustpositif.app/" }]
        ]
      });

      // 2. Alert Admin ID if not already in admin chat
      if (adminId && chatId !== adminId) {
        await sendTelegramMessage(botToken, adminId, `🔔 <b>Notifikasi Re-indexing Baru</b>\n${reportText}`);
      }
      break;
    }

    case "/ticket": {
      if (args.length === 0) {
        await sendTelegramMessage(botToken, chatId, "Format: /ticket tulis kendala yang dialami.");
        return;
      }
      if (!env.DB) {
        await sendTelegramMessage(botToken, chatId, "⛔ Database belum terhubung. Tiket belum dibuat.");
        return;
      }

      const ticketIssue = args.join(" ").slice(0, 2000);
      const now = Date.now();
      let ticketId = "";

      for (let attempt = 0; attempt < 8; attempt += 1) {
        const candidate = `TKT-${1000 + Math.floor(Math.random() * 9000)}`;
        const insertStatement = env.DB.prepare(
          `INSERT OR IGNORE INTO member_tickets
            (ticketCode, memberUsername, category, priority, domainAffected, issueDescription,
             status, reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId, telegramChatId)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).bind(
          candidate, fromUsername.slice(0, 80), "Lainnya", "TINGGI", "", ticketIssue,
          "OPEN", "TELEGRAM_BOT", now, now, "", senderId, chatId
        );
        // The dependent INSERT...SELECT statements only write audit rows when the ticket INSERT succeeded.
        const historyStatement = env.DB.prepare(
          `INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, changedByTelegramUserId, changedByUsername, source, createdAt)
           SELECT ?, NULL, 'OPEN', 'Tiket dibuat melalui Telegram Bot', ?, ?, 'TELEGRAM_BOT', ? WHERE changes() > 0`
        ).bind(candidate, senderId, fromUsername, now);
        const auditStatement = env.DB.prepare(
          `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)
           SELECT ?, ?, ?, 'CREATE', 'ticket', ?, ?, ? WHERE changes() > 0`
        ).bind(senderId, fromUsername, isAdmin ? "ADMIN" : "MEMBER", candidate, JSON.stringify({ status: "OPEN", reportedVia: "TELEGRAM_BOT" }), now);
        const insertedBatch = await env.DB.batch([insertStatement, historyStatement, auditStatement]);

        if (insertedBatch[0]?.meta?.changes > 0) {
          ticketId = candidate;
          break;
        }
      }

      if (!ticketId) {
        await sendTelegramMessage(botToken, chatId, "⛔ Tiket gagal disimpan. Silakan kirim ulang beberapa saat lagi.");
        return;
      }

      const ticketConfirm = `🎫 <b>TIKET BERHASIL DISIMPAN</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>ID Tiket:</b> <code>#${ticketId}</code>\n` +
        `• <b>Pelapor:</b> ${escapeHtml(fromUsername)}\n` +
        `• <b>Status:</b> 🟡 <b>OPEN</b>\n` +
        `• <b>Kendala:</b> ${escapeHtml(ticketIssue)}\n` +
        `• <b>Waktu:</b> ${new Date(now).toISOString()}\n` +
        `━━━━━━━━━━━━━━━━━━━━━━`;

      await sendTelegramMessage(botToken, chatId, ticketConfirm);
      if (adminId && chatId !== adminId) {
        await sendTelegramMessage(botToken, adminId, `🚨 <b>TIKET BARU #${ticketId}</b>\n${ticketConfirm}`);
      }
      break;
    }

    case "/status_ticket": {
      const ticketId = args[0] ? args[0].replace(/^#/, "").toUpperCase() : "";
      const requestedStatus = args[1] ? args[1].toUpperCase() : "";

      if (!ticketId) {
        await sendTelegramMessage(botToken, chatId, "Format: /status_ticket TKT-1234 [OPEN|DIPROSES|SELESAI|DITOLAK]");
        return;
      }
      if (!env.DB) {
        await sendTelegramMessage(botToken, chatId, "⛔ Database belum terhubung.");
        return;
      }

      const ticket = await env.DB.prepare(
        "SELECT ticketCode, status, telegramUserId, telegramChatId FROM member_tickets WHERE ticketCode = ? LIMIT 1"
      ).bind(ticketId).first();

      if (!ticket) {
        await sendTelegramMessage(botToken, chatId, `Tiket <code>${escapeHtml(ticketId)}</code> tidak ditemukan.`);
        return;
      }

      const isOwner = ticket.telegramUserId === senderId;
      if (!isAdmin && !isOwner) {
        await sendTelegramMessage(botToken, chatId, "⛔ Anda tidak memiliki akses untuk melihat tiket ini.");
        return;
      }

      let displayStatus = ticket.status;
      if (requestedStatus) {
        if (!isAdmin) {
          await sendTelegramMessage(botToken, chatId, "⛔ Hanya admin yang dapat mengubah status tiket.");
          return;
        }
        const allowedStatuses = ["OPEN", "DIPROSES", "SELESAI", "DITOLAK"];
        if (!allowedStatuses.includes(requestedStatus)) {
          await sendTelegramMessage(botToken, chatId, "Status tidak valid. Gunakan OPEN, DIPROSES, SELESAI, atau DITOLAK.");
          return;
        }

        if (requestedStatus !== ticket.status) {
          const changedAt = Date.now();
          const updateStatement = env.DB.prepare(
            "UPDATE member_tickets SET status = ?, lastUpdated = ? WHERE ticketCode = ? AND status = ?"
          ).bind(requestedStatus, changedAt, ticketId, ticket.status);
          const auditStatement = env.DB.prepare(
            `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)
             SELECT ?, ?, 'ADMIN', 'UPDATE_STATUS', 'ticket', ?, ?, ? WHERE changes() > 0`
          ).bind(senderId, fromUsername, ticketId, JSON.stringify({ oldStatus: ticket.status, newStatus: requestedStatus }), changedAt);
          const historyStatement = env.DB.prepare(
            `INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, changedByTelegramUserId, changedByUsername, source, createdAt)
             SELECT ?, ?, ?, 'Perubahan status melalui Telegram Bot', ?, ?, 'TELEGRAM_BOT', ? WHERE changes() > 0 AND ? <> ?`
          ).bind(ticketId, ticket.status, requestedStatus, senderId, fromUsername, changedAt, ticket.status, requestedStatus);
          const results = await env.DB.batch([updateStatement, auditStatement, historyStatement]);
          if (!results[0]?.meta || results[0].meta.changes === 0) {
            await sendTelegramMessage(botToken, chatId, "⚠️ Status tiket berubah bersamaan. Periksa ulang dengan /status_ticket.");
            return;
          }
        }
        displayStatus = requestedStatus;
      }

      await sendTelegramMessage(
        botToken,
        chatId,
        `📋 <b>STATUS TIKET ${escapeHtml(ticketId)}</b>\n• Status: <b>${escapeHtml(displayStatus)}</b>`
      );
      break;
    }


    case "/gsc_test": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Perintah Google Search Console hanya tersedia untuk admin.");
        return;
      }
      try {
        const accessToken = await getGoogleAccessToken(env);
        const result = await googleSearchConsoleRequest(accessToken, "GET", "https://www.googleapis.com/webmasters/v3/sites");
        const sites = result.data.siteEntry || [];
        await sendTelegramMessage(botToken, chatId,
          "✅ <b>GSC API TERHUBUNG</b>\n" +
          "• Autentikasi OAuth: berhasil\n" +
          "• Search Console API: HTTP " + result.status + "\n" +
          "• Properti dapat diakses: <b>" + sites.length + "</b>\n\n" +
          "<i>Ini membuktikan API dapat diakses; bukan bukti setiap properti sudah terverifikasi.</i>");
      } catch (error) {
        await sendTelegramMessage(botToken, chatId,
          "❌ <b>GSC API BELUM SIAP</b>\n" + escapeHtml(error.message || "Autentikasi atau permintaan Google gagal.") + "\n\n" +
          "Periksa GSC_CLIENT_ID, GSC_CLIENT_SECRET, GSC_REFRESH_TOKEN dan izin Search Console akun Google.");
      }
      break;
    }

    case "/gsc_sites": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Perintah Google Search Console hanya tersedia untuk admin.");
        return;
      }
      try {
        const accessToken = await getGoogleAccessToken(env);
        const result = await googleSearchConsoleRequest(accessToken, "GET", "https://www.googleapis.com/webmasters/v3/sites");
        const sites = result.data.siteEntry || [];
        if (sites.length === 0) {
          await sendTelegramMessage(botToken, chatId,
            "Belum ada properti Search Console yang dapat diakses oleh akun OAuth Adminku.");
          return;
        }
        const lines = sites.slice(0, 25).map((site, index) =>
          (index + 1) + ". <code>" + escapeHtml(site.siteUrl || "(site tidak diketahui)") + "</code>\n" +
          "   Izin: <b>" + escapeHtml(site.permissionLevel || "UNKNOWN") + "</b>"
        );
        await sendTelegramMessage(botToken, chatId,
          "🌐 <b>PROPERTI GOOGLE SEARCH CONSOLE (" + sites.length + ")</b>\n\n" + lines.join("\n") +
          (sites.length > 25 ? "\n\n<i>Menampilkan 25 properti pertama.</i>" : ""));
      } catch (error) {
        await sendTelegramMessage(botToken, chatId,
          "❌ Gagal membaca daftar properti GSC:\n" + escapeHtml(error.message || "Permintaan Google gagal."));
      }
      break;
    }

    case "/gsc_inspect": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Perintah Google Search Console hanya tersedia untuk admin.");
        return;
      }
      if (!args[0]) {
        await sendTelegramMessage(botToken, chatId,
          "Format: <code>/gsc_inspect https://mami44.com/halaman</code>\n" +
          "Opsional properti: <code>/gsc_inspect https://mami44.com/halaman https://mami44.com/</code>\n" +
          "Untuk Domain Property, gunakan <code>sc-domain:mami44.com</code> sebagai argumen kedua.");
        return;
      }
      if (!env.DB) {
        await sendTelegramMessage(botToken, chatId, "⛔ D1 belum terhubung; hasil tidak dapat dicatat.");
        return;
      }
      try {
        const inspectionUrl = normalizeGscInspectionUrl(args[0]);
        const siteUrl = normalizeGscSiteProperty(args[1] || (new URL(inspectionUrl).origin + "/"));
        if (!isUrlWithinGscProperty(inspectionUrl, siteUrl)) {
          throw new Error("URL yang diperiksa tidak berada di dalam properti Search Console yang dipilih.");
        }

        const accessToken = await getGoogleAccessToken(env);
        const apiResult = await googleSearchConsoleRequest(accessToken, "POST",
          "https://searchconsole.googleapis.com/v1/urlInspection/index:inspect",
          { inspectionUrl: inspectionUrl, siteUrl: siteUrl, languageCode: "id-ID" });
        const inspection = apiResult.data.inspectionResult || {};
        const indexStatus = inspection.indexStatusResult || {};
        const propertyHome = getGscPropertyHomepage(siteUrl);
        const publicSignals = await inspectPublicGscSignals(propertyHome);
        let sitemapCount = 0;
        let sitemapCheckNote = "Sitemap tidak diperiksa.";
        try {
          const sitemapResult = await googleSearchConsoleRequest(accessToken, "GET",
            "https://www.googleapis.com/webmasters/v3/sites/" + encodeURIComponent(siteUrl) + "/sitemaps");
          sitemapCount = (sitemapResult.data.sitemap || []).length;
          sitemapCheckNote = "Sitemap terdaftar di Search Console: " + sitemapCount;
        } catch (sitemapError) {
          sitemapCheckNote = "Pemeriksaan daftar sitemap gagal: " + sitemapError.message;
        }

        const trackingId = createGscTrackingId();
        const diagnostic = {
          source: "Google Search Console URL Inspection API",
          apiHttpStatus: apiResult.status,
          inspectionUrl: inspectionUrl,
          siteUrl: siteUrl,
          verdict: indexStatus.verdict || "UNKNOWN",
          coverageState: indexStatus.coverageState || "",
          indexingState: indexStatus.indexingState || "",
          pageFetchState: indexStatus.pageFetchState || "",
          robotsTxtState: indexStatus.robotsTxtState || "",
          lastCrawlTime: indexStatus.lastCrawlTime || null,
          googleCanonical: indexStatus.googleCanonical || "",
          userCanonical: indexStatus.userCanonical || "",
          sitemapCount: sitemapCount,
          sitemapCheckNote: sitemapCheckNote,
          publicPageHttpStatus: publicSignals.httpStatus,
          googleVerificationMetaTagFound: publicSignals.verificationTagFound
        };
        await saveGscSubmissionLog(env, {
          url: inspectionUrl,
          domain: getGscPropertyDomain(siteUrl),
          httpStatus: publicSignals.httpStatus,
          verificationTagFound: publicSignals.verificationTagFound ? 1 : 0,
          sitemapDetected: sitemapCount > 0 ? 1 : 0,
          indexingStatus: indexStatus.verdict || "UNKNOWN",
          diagnosticMessage: JSON.stringify(diagnostic),
          gscTrackingId: trackingId
        });
        await recordBotAudit(env, senderId, fromUsername, "INSPECT", "gsc_url", trackingId, { inspectionUrl, siteUrl, verdict: indexStatus.verdict || "UNKNOWN" });

        await sendTelegramMessage(botToken, chatId,
          "🔎 <b>HASIL URL INSPECTION GOOGLE</b>\n" +
          "• URL: <code>" + escapeHtml(inspectionUrl) + "</code>\n" +
          "• Properti: <code>" + escapeHtml(siteUrl) + "</code>\n" +
          "• Verdict indeks: <b>" + escapeHtml(indexStatus.verdict || "UNKNOWN") + "</b>\n" +
          "• Coverage: " + escapeHtml(indexStatus.coverageState || "-") + "\n" +
          "• Indexing state: " + escapeHtml(indexStatus.indexingState || "-") + "\n" +
          "• Last crawl: " + escapeHtml(indexStatus.lastCrawlTime || "-") + "\n" +
          "• HTTP halaman saat diperiksa: " + (publicSignals.httpStatus || "Tidak terjangkau") + "\n" +
          "• Meta verification tag ditemukan: " + (publicSignals.verificationTagFound ? "Ya" : "Tidak") + "\n" +
          "• " + escapeHtml(sitemapCheckNote) + "\n" +
          "• Tracking ID: <code>" + trackingId + "</code>\n\n" +
          "<i>URL Inspection membaca status URL dalam indeks Google; ini bukan tombol Request Indexing.</i>");
      } catch (error) {
        await sendTelegramMessage(botToken, chatId,
          "❌ URL Inspection gagal:\n" + escapeHtml(error.message || "Permintaan Google gagal."));
      }
      break;
    }

    case "/gsc_sitemaps": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Perintah Google Search Console hanya tersedia untuk admin.");
        return;
      }
      if (!args[0]) {
        await sendTelegramMessage(botToken, chatId,
          "Format: <code>/gsc_sitemaps https://mami44.com/</code>\n" +
          "Untuk Domain Property: <code>/gsc_sitemaps sc-domain:mami44.com</code>");
        return;
      }
      try {
        const siteUrl = normalizeGscSiteProperty(args[0]);
        const accessToken = await getGoogleAccessToken(env);
        const result = await googleSearchConsoleRequest(accessToken, "GET",
          "https://www.googleapis.com/webmasters/v3/sites/" + encodeURIComponent(siteUrl) + "/sitemaps");
        const sitemaps = result.data.sitemap || [];
        if (sitemaps.length === 0) {
          await sendTelegramMessage(botToken, chatId,
            "Belum ada sitemap yang tercatat di Search Console untuk <code>" + escapeHtml(siteUrl) + "</code>.");
          return;
        }
        const lines = sitemaps.slice(0, 20).map((item, index) =>
          (index + 1) + ". <code>" + escapeHtml(item.path || "(URL tidak tersedia)") + "</code>\n" +
          "   Status: " + escapeHtml(item.isPending ? "PENDING" : "TERCATAT") + "\n" +
          "   Terakhir dikirim: " + escapeHtml(item.lastSubmitted || "-") + "\n" +
          "   Terakhir diunduh: " + escapeHtml(item.lastDownloaded || "-")
        );
        await sendTelegramMessage(botToken, chatId,
          "🗺️ <b>SITEMAP SEARCH CONSOLE</b>\nProperti: <code>" + escapeHtml(siteUrl) + "</code>\n\n" +
          lines.join("\n") + (sitemaps.length > 20 ? "\n\n<i>Menampilkan 20 sitemap pertama.</i>" : ""));
      } catch (error) {
        await sendTelegramMessage(botToken, chatId,
          "❌ Gagal membaca sitemap Search Console:\n" + escapeHtml(error.message || "Permintaan Google gagal."));
      }
      break;
    }

    case "/gsc_submit_sitemap": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Perintah Google Search Console hanya tersedia untuk admin.");
        return;
      }
      if (!args[0] || !args[1]) {
        await sendTelegramMessage(botToken, chatId,
          "Format: <code>/gsc_submit_sitemap https://mami44.com/ https://mami44.com/sitemap.xml</code>\n" +
          "Untuk Domain Property: <code>/gsc_submit_sitemap sc-domain:mami44.com https://mami44.com/sitemap.xml</code>");
        return;
      }
      if (!env.DB) {
        await sendTelegramMessage(botToken, chatId, "⛔ D1 belum terhubung; hasil pengiriman tidak dapat dicatat.");
        return;
      }
      try {
        const siteUrl = normalizeGscSiteProperty(args[0]);
        const sitemapUrl = normalizeGscInspectionUrl(args[1]);
        if (!isUrlWithinGscProperty(sitemapUrl, siteUrl)) {
          throw new Error("URL sitemap tidak berada di dalam properti Search Console yang dipilih.");
        }
        const accessToken = await getGoogleAccessToken(env);
        const apiResult = await googleSearchConsoleRequest(accessToken, "PUT",
          "https://www.googleapis.com/webmasters/v3/sites/" + encodeURIComponent(siteUrl) +
          "/sitemaps/" + encodeURIComponent(sitemapUrl));
        const publicSignals = await inspectPublicGscSignals(getGscPropertyHomepage(siteUrl));
        const trackingId = createGscTrackingId();
        const diagnostic = {
          source: "Google Search Console Sitemaps API",
          apiHttpStatus: apiResult.status,
          siteUrl: siteUrl,
          sitemapUrl: sitemapUrl,
          acceptedByGoogleApi: true,
          note: "Penerimaan sitemap bukan jaminan seluruh URL akan di-crawl atau diindeks."
        };
        await saveGscSubmissionLog(env, {
          url: sitemapUrl,
          domain: getGscPropertyDomain(siteUrl),
          httpStatus: publicSignals.httpStatus || apiResult.status,
          verificationTagFound: publicSignals.verificationTagFound ? 1 : 0,
          sitemapDetected: 1,
          indexingStatus: "SITEMAP_SUBMITTED",
          diagnosticMessage: JSON.stringify(diagnostic),
          gscTrackingId: trackingId
        });
        await recordBotAudit(env, senderId, fromUsername, "SUBMIT_SITEMAP", "gsc_sitemap", trackingId, { siteUrl, sitemapUrl, apiHttpStatus: apiResult.status });
        await sendTelegramMessage(botToken, chatId,
          "✅ <b>SITEMAP DITERIMA API GOOGLE</b>\n" +
          "• Properti: <code>" + escapeHtml(siteUrl) + "</code>\n" +
          "• Sitemap: <code>" + escapeHtml(sitemapUrl) + "</code>\n" +
          "• HTTP API: " + apiResult.status + "\n" +
          "• Tracking ID: <code>" + trackingId + "</code>\n\n" +
          "<i>Google menerima pendaftaran sitemap. Ini tidak menjamin URL langsung di-crawl atau diindeks. Gunakan /gsc_sitemaps untuk memeriksa catatan sitemap.</i>");
      } catch (error) {
        await sendTelegramMessage(botToken, chatId,
          "❌ Pengiriman sitemap gagal:\n" + escapeHtml(error.message || "Permintaan Google gagal.") + "\n\n" +
          "Periksa apakah OAuth memiliki scope webmasters (read/write) dan akun OAuth memiliki akses ke properti.");
      }
      break;
    }

    case "/trustpositif": {
      const targetDomain = args[0] ? args[0].replace(/^https?:\/\//, "").replace(/\/.*$/, "").toLowerCase() : baseDomain;
      if (!isValidDomainName(targetDomain)) {
        await sendTelegramMessage(botToken, chatId, "Format: /trustpositif domainanda.com. Masukkan domain yang valid.");
        return;
      }
      const safeTargetDomain = escapeHtml(targetDomain);
      const trustText = `🛡️ <b>Pengecekan TrustPositif (Kominfo)</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>Domain:</b> <code>${safeTargetDomain}</code>\n` +
        `• <b>Akses Database:</b> <a href="https://trustpositif.app/">https://trustpositif.app/</a>\n` +
        `• <b>Bot Rekomendasi:</b> <a href="https://t.me/reqweb_bot">@reqweb_bot</a>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `Klik tombol di bawah untuk verifikasi langsung di TrustPositif atau kirim request unblock/alternatif via @reqweb_bot:`;

      await sendTelegramMessage(botToken, chatId, trustText, {
        inline_keyboard: [
          [{ text: "🔍 Buka trustpositif.app", url: "https://trustpositif.app/" }],
          [{ text: "🤖 Buka @reqweb_bot", url: reqWebBotUrl }]
        ]
      });
      break;
    }

    case "/list_domains": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Daftar domain hanya tersedia untuk admin.");
        return;
      }
      if (!env.DB) {
        await sendTelegramMessage(botToken, chatId, "⛔ Database belum terhubung.");
        return;
      }
      const result = await env.DB.prepare(
        "SELECT domainName, status, gscIndexingStatus FROM domain_requests ORDER BY createdAt DESC LIMIT 20"
      ).all();
      const rows = result.results || [];
      if (rows.length === 0) {
        await sendTelegramMessage(botToken, chatId, "Belum ada domain yang terdaftar di database.");
        return;
      }

      const lines = rows.map((row, index) =>
        `${index + 1}. <code>${escapeHtml(row.domainName)}</code> — ${escapeHtml(row.status)} / ${escapeHtml(row.gscIndexingStatus)}`
      );
      await sendTelegramMessage(botToken, chatId, `🌐 <b>DOMAIN DARI DATABASE</b>\n${lines.join("\n")}`);
      break;
    }

    case "/report": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, "⛔ Laporan hanya tersedia untuk admin.");
        return;
      }
      if (!env.DB) {
        await sendTelegramMessage(botToken, chatId, "⛔ Database belum terhubung.");
        return;
      }

      const totals = await env.DB.prepare(
        `SELECT
          (SELECT COUNT(*) FROM member_tickets) AS total_tickets,
          (SELECT COUNT(*) FROM member_tickets WHERE status = 'OPEN') AS open_tickets,
          (SELECT COUNT(*) FROM domain_requests) AS total_domains,
          (SELECT COUNT(*) FROM domain_requests WHERE status = 'ACTIVE') AS active_domains,
          (SELECT COUNT(*) FROM gsc_submission_logs) AS gsc_audits`
      ).first();

      const reportMsg = `📊 <b>RINGKASAN DATABASE</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• Total tiket: <b>${totals?.total_tickets ?? 0}</b>\n` +
        `• Tiket OPEN: <b>${totals?.open_tickets ?? 0}</b>\n` +
        `• Total domain: <b>${totals?.total_domains ?? 0}</b>\n` +
        `• Domain aktif: <b>${totals?.active_domains ?? 0}</b>\n` +
        `• Catatan audit GSC: <b>${totals?.gsc_audits ?? 0}</b>\n` +
        `• Waktu: ${new Date().toISOString()}`;
      await sendTelegramMessage(botToken, chatId, reportMsg);
      break;
    }

    default: {
      if (!isGroup) {
        await sendTelegramMessage(botToken, chatId, `❓ Perintah tidak dikenali: <code>${escapeHtml(command)}</code>\nKetik <code>/start</code> untuk melihat daftar perintah yang tersedia.`);
      }
      break;
    }
  }
}

/**
 * Send HTTP POST to Telegram API
 */
async function sendTelegramMessage(token, chatId, text, replyMarkup = null) {
  const url = `https://api.telegram.org/bot${token}/sendMessage`;
  const body = {
    chat_id: chatId,
    text: text,
    parse_mode: "HTML",
    disable_web_page_preview: false
  };
  if (replyMarkup) {
    body.reply_markup = replyMarkup;
  }

  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  return res.json();
}


async function recordBotAudit(env, telegramUserId, username, action, resourceType, resourceId, details = {}) {
  if (!env.DB) throw new Error("Binding D1 DB belum tersedia untuk audit.");
  await env.DB.prepare(
    `INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)
     VALUES (?, ?, 'ADMIN', ?, ?, ?, ?, ?)`
  ).bind(telegramUserId, username, action, resourceType, String(resourceId), JSON.stringify(details).slice(0, 8000), Date.now()).run();
}

async function getGoogleAccessToken(env) {
  const clientId = (env.GSC_CLIENT_ID || "").trim();
  const clientSecret = (env.GSC_CLIENT_SECRET || "").trim();
  const refreshToken = (env.GSC_REFRESH_TOKEN || "").trim();
  if (!clientId || !clientSecret || !refreshToken) {
    throw new Error("Kredensial OAuth GSC belum lengkap. Tambahkan GSC_CLIENT_ID, GSC_CLIENT_SECRET, dan GSC_REFRESH_TOKEN sebagai Runtime Secrets.");
  }

  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: refreshToken,
      grant_type: "refresh_token"
    })
  });
  const tokenData = await tokenResponse.json().catch(() => ({}));
  if (!tokenResponse.ok || !tokenData.access_token) {
    const reason = tokenData.error_description || tokenData.error || ("HTTP " + tokenResponse.status);
    throw new Error("Google OAuth gagal memperbarui token: " + reason);
  }
  return tokenData.access_token;
}

async function googleSearchConsoleRequest(accessToken, method, url, body = undefined) {
  const response = await fetch(url, {
    method: method,
    headers: {
      Authorization: "Bearer " + accessToken,
      ...(body === undefined ? {} : { "Content-Type": "application/json" })
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const raw = await response.text();
  let data = {};
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = { rawResponse: raw.slice(0, 1000) };
    }
  }
  if (!response.ok) {
    const detail = data.error?.message || data.error_description || ("HTTP " + response.status);
    throw new Error("Google Search Console API: " + detail);
  }
  return { status: response.status, data: data };
}

function normalizeGscSiteProperty(value) {
  const input = String(value || "").trim();
  if (!input) throw new Error("Properti Search Console wajib diisi.");
  if (/^sc-domain:/i.test(input)) {
    const domain = input.slice("sc-domain:".length).toLowerCase();
    if (!isValidDomainName(domain)) throw new Error("Format Domain Property tidak valid. Gunakan sc-domain:domainanda.com.");
    return "sc-domain:" + domain;
  }

  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(input) ? input : "https://" + input;
  const parsed = new URL(candidate);
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error("Properti URL-prefix harus berupa URL http/https yang valid tanpa query atau fragment.");
  }
  if (!isValidDomainName(parsed.hostname)) throw new Error("Hostname properti tidak valid.");
  if (!parsed.pathname.endsWith("/")) parsed.pathname += "/";
  return parsed.toString();
}

function normalizeGscInspectionUrl(value) {
  const input = String(value || "").trim();
  const candidate = /^[a-z][a-z0-9+.-]*:\/\//i.test(input) ? input : "https://" + input;
  const parsed = new URL(candidate);
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password) {
    throw new Error("URL harus menggunakan http atau https dan tidak boleh berisi kredensial.");
  }
  if (!isValidDomainName(parsed.hostname)) throw new Error("Domain URL tidak valid.");
  return parsed.toString();
}

function isUrlWithinGscProperty(targetUrl, siteUrl) {
  const target = new URL(targetUrl);
  if (siteUrl.startsWith("sc-domain:")) {
    const domain = siteUrl.slice("sc-domain:".length);
    return target.hostname === domain || target.hostname.endsWith("." + domain);
  }
  const property = new URL(siteUrl);
  return target.protocol === property.protocol &&
    target.hostname === property.hostname &&
    target.port === property.port &&
    target.pathname.startsWith(property.pathname);
}

function getGscPropertyHomepage(siteUrl) {
  if (siteUrl.startsWith("sc-domain:")) {
    return "https://" + siteUrl.slice("sc-domain:".length) + "/";
  }
  return siteUrl;
}

function getGscPropertyDomain(siteUrl) {
  return siteUrl.startsWith("sc-domain:")
    ? siteUrl.slice("sc-domain:".length)
    : new URL(siteUrl).hostname;
}

async function inspectPublicGscSignals(homepageUrl) {
  try {
    const response = await fetch(homepageUrl, {
      method: "GET",
      redirect: "follow",
      headers: { "User-Agent": "Adminku-GSC-Verification-Check/1.0" }
    });
    const html = await response.text();
    const verificationTagFound = /<meta\b(?=[^>]*\bname\s*=\s*["']google-site-verification["'])[^>]*>/i.test(html);
    return { httpStatus: response.status, verificationTagFound: verificationTagFound };
  } catch {
    return { httpStatus: 0, verificationTagFound: false };
  }
}

function createGscTrackingId() {
  const suffix = typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID().replace(/-/g, "").slice(0, 10).toUpperCase()
    : Math.floor(Math.random() * 1e10).toString(36).toUpperCase();
  return "GSC-CF-" + Date.now().toString(36).toUpperCase() + "-" + suffix;
}

async function saveGscSubmissionLog(env, entry) {
  if (!env.DB) throw new Error("Binding D1 DB belum tersedia.");
  await env.DB.prepare(
    "INSERT INTO gsc_submission_logs " +
    "(url, domain, searchEngine, httpStatus, verificationTagFound, sitemapDetected, " +
    "indexingStatus, diagnosticMessage, gscTrackingId, submittedAt) " +
    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
  ).bind(
    entry.url,
    entry.domain,
    "Google Search Console API",
    Number.isInteger(entry.httpStatus) ? entry.httpStatus : 0,
    entry.verificationTagFound ? 1 : 0,
    entry.sitemapDetected ? 1 : 0,
    entry.indexingStatus,
    String(entry.diagnosticMessage || "").slice(0, 8000),
    entry.gscTrackingId,
    Date.now()
  ).run();
}

function isValidDomainName(value) {
  if (typeof value !== "string" || value.length > 253) return false;
  return /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$/i.test(value);
}

function escapeHtml(str) {
  return (str || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
