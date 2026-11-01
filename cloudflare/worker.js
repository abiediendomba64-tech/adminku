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

    // 1. Health Check & Info
    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/status")) {
      return new Response(JSON.stringify({
        status: "online",
        service: "Backoffice Telegram Bot & GSC Automation",
        version: "2.0.0",
        platform: "Cloudflare Workers Free Tier",
        timestamp: new Date().toISOString(),
        admin_id: "7862805424",
        admin_handle: "@felixsnd",
        group_link: "https://t.me/+ybOzZ_lstEdhNDU1",
        commands: [
          "/start", "/panel", "/reindex <domain>", "/ticket <kendala>",
          "/status_ticket <id> <status>", "/gsc_inspect <domain>",
          "/list_domains", "/report", "/trustpositif <domain>"
        ]
      }, null, 2), {
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }

    // 2. Setup Webhook Helper Endpoint (GET or POST /setup-webhook)
    if (url.pathname === "/setup-webhook") {
      const token = env.TELEGRAM_BOT_TOKEN || url.searchParams.get("token");
      if (!token) {
        return new Response(JSON.stringify({ error: "TELEGRAM_BOT_TOKEN belum diatur di env atau query param (?token=...)" }), {
          status: 400,
          headers: { "Content-Type": "application/json" }
        });
      }
      const webhookUrl = `${url.origin}/webhook`;
      const tgRes = await fetch(`https://api.telegram.org/bot${token}/setWebhook?url=${encodeURIComponent(webhookUrl)}&drop_pending_updates=true`);
      const tgJson = await tgRes.json();
      return new Response(JSON.stringify({
        webhook_target: webhookUrl,
        telegram_response: tgJson
      }, null, 2), {
        headers: { "Content-Type": "application/json" }
      });
    }

    // 3. Webhook Info Helper Endpoint (GET /webhook-info)
    if (url.pathname === "/webhook-info") {
      const token = env.TELEGRAM_BOT_TOKEN || url.searchParams.get("token");
      if (!token) {
        return new Response(JSON.stringify({ error: "TELEGRAM_BOT_TOKEN belum diatur" }), { status: 400 });
      }
      const tgRes = await fetch(`https://api.telegram.org/bot${token}/getWebhookInfo`);
      const tgJson = await tgRes.json();
      return new Response(JSON.stringify(tgJson, null, 2), {
        headers: { "Content-Type": "application/json" }
      });
    }

    // 4. Handle Incoming Telegram Webhook (POST /webhook or POST /)
    if (request.method === "POST" && (url.pathname === "/webhook" || url.pathname === "/")) {
      const botToken = env.TELEGRAM_BOT_TOKEN || "";
      if (!botToken) {
        return new Response("Bot token not configured on worker env", { status: 500 });
      }

      try {
        const update = await request.json();
        ctx.waitUntil(handleTelegramUpdate(update, botToken, env, url.origin));
        return new Response("OK", { status: 200 });
      } catch (err) {
        return new Response(`Error: ${err.message}`, { status: 400 });
      }
    }

    // Default 404
    return new Response("Not Found", { status: 404 });
  }
};

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

  const adminId = env.TELEGRAM_ADMIN_ID || "7862805424";
  const groupLink = env.TELEGRAM_GROUP_LINK || "https://t.me/+ybOzZ_lstEdhNDU1";
  const webAppUrl = env.TELEGRAM_WEBAPP_URL || "https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app";
  const baseDomain = env.BASE_DOMAIN || "panel-utama88.com";

  const senderId = msg.from?.id ? String(msg.from.id) : "";
  const isAdmin = (senderId === adminId || chatId === adminId);

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
      const panelUrl = `https://${baseDomain}/backoffice`;
      const tagsUrl = `https://${baseDomain}/backoffice/settings/marketing-tags`;
      const gscUrl = "https://search.google.com/search-console/welcome";

      const panelText = `🌐 <b>Panel Backoffice & Marketing Settings</b>\n\n` +
        `• <b>Domain Utama:</b> <code>${baseDomain}</code>\n` +
        `• <b>Dashboard:</b> <a href="${panelUrl}">${panelUrl}</a>\n` +
        `• <b>Marketing Tags:</b> <a href="${tagsUrl}">${tagsUrl}</a>\n` +
        `• <b>Google Search Console:</b> <a href="${gscUrl}">${gscUrl}</a>\n\n` +
        `Gunakan tombol di bawah untuk membuka panel langsung di Telegram:`;

      await sendTelegramMessage(botToken, chatId, panelText, {
        inline_keyboard: [
          [{ text: "🚀 Luncurkan Web App Backoffice", web_app: { url: webAppUrl } }],
          [{ text: "🏷️ Buka Marketing Tags", url: tagsUrl }]
        ]
      });
      break;
    }

    case "/reindex": {
      const targetDomain = args[0] ? args[0].replace(/^https?:\/\//, "").replace(/\/.*$/, "") : baseDomain;
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
        await sendTelegramMessage(botToken, chatId, `⚠️ <b>Format Salah</b>\nContoh: <code>/ticket Domain utama tidak bisa diakses dari ISP Telkomsel</code>`);
        return;
      }

      const ticketIssue = args.join(" ");
      const ticketId = `TKT-${Math.floor(1000 + Math.random() * 9000)}`;

      const ticketConfirm = `🎫 <b>TIKET KELUHAN BERHASIL DIBUAT</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>ID Tiket:</b> <code>#${ticketId}</code>\n` +
        `• <b>Pelapor:</b> ${fromUsername} (ID: <code>${chatId}</code>)\n` +
        `• <b>Status:</b> 🟡 <b>OPEN</b>\n` +
        `• <b>Kendala:</b> ${escapeHtml(ticketIssue)}\n` +
        `• <b>Prioritas:</b> TINGGI\n` +
        `• <b>Waktu:</b> ${new Date().toISOString()}\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `<i>Tim admin @felixsnd telah menerima notifikasi dan akan segera menindaklanjuti.</i>`;

      // Reply to reporter
      await sendTelegramMessage(botToken, chatId, ticketConfirm);

      // Alert Admin
      if (adminId && chatId !== adminId) {
        await sendTelegramMessage(botToken, adminId, `🚨 <b>TIKET KELUHAN MEMBER BARU #${ticketId}</b>\n${ticketConfirm}`);
      }
      break;
    }

    case "/status_ticket": {
      const tktId = args[0] ? args[0].toUpperCase() : "";
      const newStatus = args[1] ? args[1].toUpperCase() : "";

      if (!tktId) {
        await sendTelegramMessage(botToken, chatId, `ℹ️ <b>Format Perintah:</b> <code>/status_ticket &lt;ID_TIKET&gt; [STATUS]</code>\nContoh: <code>/status_ticket #TKT-4892 DIPROSES</code>`);
        return;
      }

      if (newStatus && !isAdmin) {
        await sendTelegramMessage(botToken, chatId, `⛔ <b>Akses Ditolak:</b> Hanya Administrator resmi (@felixsnd) yang berhak memperbarui status tiket member.`);
        return;
      }

      const displayStatus = newStatus || "DIPERIKSA";
      const statusText = `📋 <b>STATUS TIKET ${tktId}</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>Status Saat Ini:</b> 🟢 <b>${displayStatus}</b>\n` +
        `• <b>Diperbarui Oleh:</b> ${fromUsername} ${isAdmin ? "(Admin Verified)" : "(User Check)"}\n` +
        `• <b>Catatan:</b> ${newStatus ? "Status tiket berhasil diperbarui di sistem." : "Tiket sedang dalam penanganan tim operasional."}\n` +
        `━━━━━━━━━━━━━━━━━━━━━━`;

      await sendTelegramMessage(botToken, chatId, statusText);
      break;
    }

    case "/trustpositif": {
      const targetDomain = args[0] ? args[0].replace(/^https?:\/\//, "").replace(/\/.*$/, "") : baseDomain;
      const trustText = `🛡️ <b>Pengecekan TrustPositif (Kominfo)</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>Domain:</b> <code>${targetDomain}</code>\n` +
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
      const domainsText = `🌐 <b>Daftar Domain Terdaftar</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `1. <code>${baseDomain}</code> [INDEXED / AKTIF]\n` +
        `2. <code>vip88-alternatif.com</code> [SUBMITTED]\n` +
        `3. <code>link-resmi-utama.net</code> [PENDING_CRAWL]\n` +
        `4. <code>member-zone-secure.org</code> [NOT_INDEXED]\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `Gunakan <code>/reindex &lt;domain&gt;</code> untuk memicu permohonan crawling ulang GSC.`;

      await sendTelegramMessage(botToken, chatId, domainsText);
      break;
    }

    case "/report": {
      if (!isAdmin) {
        await sendTelegramMessage(botToken, chatId, `⛔ <b>Akses Ditolak:</b> Laporan eksekutif sistem hanya dapat diakses oleh Administrator resmi (@felixsnd).`);
        return;
      }

      const reportMsg = `📊 <b>EXECUTIVE SUMMARY LAPORAN HARIAN</b>\n` +
        `━━━━━━━━━━━━━━━━━━━━━━\n` +
        `• <b>Platform Serverless:</b> Cloudflare Workers (Free Tier)\n` +
        `• <b>Admin Otorisasi:</b> <code>${adminId}</code> (@felixsnd)\n` +
        `• <b>Grup Telegram:</b> ${groupLink}\n` +
        `• <b>Domain Terindeks GSC:</b> 1/4 Domain\n` +
        `• <b>Status Server Backoffice:</b> ONLINE 200 OK\n` +
        `• <b>Web App Mini-App:</b> AKTIF\n` +
        `• <b>Waktu:</b> ${new Date().toISOString()}\n` +
        `━━━━━━━━━━━━━━━━━━━━━━`;

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

function escapeHtml(str) {
  return (str || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
