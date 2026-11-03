/**
 * Cloudflare Worker for Telegram Bot & Backoffice Automation
 * Free Tier (100,000 requests/day, 0ms cold start, no server required)
 * 
 * Target Admin ID: 7862805424
 * Target Admin Handle: @felixsnd (https://t.me/felixsnd)
 * Target Telegram Group: https://t.me/+ybOzZ_lstEdhNDU1
 * Web App URL: https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

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
        version: "2.1.0",
        database,
        timestamp: new Date().toISOString()
      });
    }

    // The webhook secret is also required to access these maintenance endpoints.
    if (url.pathname === "/setup-webhook") {
      if (request.method !== "POST") {
        return new Response("Method Not Allowed", { status: 405, headers: { Allow: "POST" } });
      }

      const secret = env.TELEGRAM_WEBHOOK_SECRET || "";
      const token = env.TELEGRAM_BOT_TOKEN || "";
      if (!secret || !token) {
        return jsonResponse({ error: "Runtime secrets TELEGRAM_WEBHOOK_SECRET and TELEGRAM_BOT_TOKEN must be configured." }, 503);
      }
      if (!isAuthorizedMaintenanceRequest(request, secret)) {
        return jsonResponse({ error: "Unauthorized" }, 401);
      }

      const webhookUrl = new URL("/webhook", url.origin).toString();
      const telegramResponse = await fetch(`https://api.telegram.org/bot${token}/setWebhook`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          url: webhookUrl,
          secret_token: secret,
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

      const secret = env.TELEGRAM_WEBHOOK_SECRET || "";
      const token = env.TELEGRAM_BOT_TOKEN || "";
      if (!secret || !token) {
        return jsonResponse({ error: "Runtime secrets TELEGRAM_WEBHOOK_SECRET and TELEGRAM_BOT_TOKEN must be configured." }, 503);
      }
      if (!isAuthorizedMaintenanceRequest(request, secret)) {
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
        `• <code>/gsc_inspect &lt;domain&gt;</code> — Inspeksi Google Search Console\n` +
        `• <code>/trustpositif &lt;domain&gt;</code> — Cek database blokir Kominfo\n` +
        `• <code>/list_domains</code> — Daftar domain aktif\n` +
        `• <code>/report</code> — Ringkasan sistem & tiket ke admin\n\n` +
        `Klik tombol di bawah untuk membuka Web App interaktif:`;

      await sendTelegramMessage(botToken, chatId, welcomeText, {
        inline_keyboard: [
          [{ text: "🌐 Buka Panel Web App", web_app: { url: webAppUrl } }],
          [{ text: "💬 Hubungi Admin (@felixsnd)", url: "https://t.me/felixsnd" }]
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

      const reportText = `⚡ <b>[LAPORAN RE-INDEXING GSC]</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>Domain:</b> <code>${targetDomain}</code>\n` +
        `• <b>Tracking ID:</b> <code>#${trackingCode}</code>\n` +
        `• <b>HTTP Status:</b> <code>${httpStatus}</code>\n` +
        `• <b>Meta Verification:</b> ${metaTagFound ? "✅ TERDETEKSI" : "⚠️ BELUM TERDETEKSI"}\n` +
        `• <b>Sitemap.xml:</b> ${sitemapFound ? "✅ VALID" : "❌ TIDAK DITEMUKAN"}\n` +
        `• <b>Status Antrean GSC:</b> ⏳ PENDING_CRAWL\n` +
        `• <b>Dipicu oleh:</b> ${fromUsername} (Cloudflare Worker 24/7)\n` +
        `• <b>Waktu:</b> ${new Date().toISOString()}\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `<i>Admin diinstruksikan untuk memantau URL Inspection di Google Search Console.</i>`;

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
        const inserted = await env.DB.prepare(
          `INSERT OR IGNORE INTO member_tickets
            (ticketCode, memberUsername, category, priority, domainAffected, issueDescription,
             status, reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId, telegramChatId)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).bind(
          candidate,
          fromUsername.slice(0, 80),
          "Lainnya",
          "TINGGI",
          "",
          ticketIssue,
          "OPEN",
          "TELEGRAM_BOT",
          now,
          now,
          "",
          senderId,
          chatId
        ).run();

        if (inserted.meta?.changes > 0) {
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

        await env.DB.prepare(
          "UPDATE member_tickets SET status = ?, lastUpdated = ? WHERE ticketCode = ?"
        ).bind(requestedStatus, Date.now(), ticketId).run();
        displayStatus = requestedStatus;
      }

      await sendTelegramMessage(
        botToken,
        chatId,
        `📋 <b>STATUS TIKET ${escapeHtml(ticketId)}</b>\n• Status: <b>${escapeHtml(displayStatus)}</b>`
      );
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
          [{ text: "🤖 Buka @reqweb_bot", url: "https://t.me/reqweb_bot" }]
        ]
      });
      break;
    }

    case "/list_domains": {
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
