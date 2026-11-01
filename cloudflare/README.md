# ☁️ Panduan Deploy Cloudflare Workers (100% Free Tier)

Cloudflare Workers menyediakan kuota **100.000 request/hari GRATIS** dengan latensi sangat cepat (global edge). Ini membuat bot Telegram Anda berjalan **24/7 non-stop** tanpa memerlukan VPS atau membiarkan HP tetap menyala.

---

### Cara 1: Deploy Lewat Dashboard Cloudflare (Tanpa Install Apapun / 2 Menit)

1. Buka [https://dash.cloudflare.com/](https://dash.cloudflare.com/) lalu login / daftar gratis.
2. Di menu samping kiri, klik **Workers & Pages** -> **Create application** -> **Create Worker**.
3. Beri nama worker Anda (misal: `backoffice-bot`) lalu klik **Deploy**.
4. Setelah ter-deploy, klik tombol **Edit code** (Quick Edit di browser).
5. Hapus semua kode bawaan, lalu salin (paste) seluruh isi file `cloudflare/worker.js`.
6. Klik tombol **Deploy** di pojok kanan atas.
7. Masuk ke tab **Settings** -> **Variables and Secrets** di worker Anda:
   - Tambahkan Environment Variable:
     - `TELEGRAM_BOT_TOKEN`: Masukkan token bot dari @BotFather
     - `TELEGRAM_ADMIN_ID`: `7862805424`
     - `TELEGRAM_ADMIN_USERNAME`: `felixsnd`
     - `TELEGRAM_GROUP_LINK`: `https://t.me/+ybOzZ_lstEdhNDU1`
     - `TELEGRAM_WEBAPP_URL`: `https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app`
     - `BASE_DOMAIN`: `panel-utama88.com`
   - Klik **Save and deploy**.
8. Hubungkan Webhook Telegram ke Cloudflare:
   - Buka URL berikut di browser Anda:
     `https://<worker-anda>.workers.dev/setup-webhook`
   - Atau gunakan tombol **"Pasang Webhook ke Cloudflare"** langsung di aplikasi Android ini!

---

### Cara 2: Deploy Lewat Terminal / Wrangler CLI

```bash
# 1. Masuk ke folder cloudflare
cd cloudflare

# 2. Login ke akun Cloudflare
npx wrangler login

# 3. Deploy langsung ke Cloudflare
npx wrangler deploy

# 4. Tambahkan secret bot token
npx wrangler secret put TELEGRAM_BOT_TOKEN
```

---

### Fitur Cloudflare Worker Bot:
- `/start` : Sambutan & tombol interaktif Web App Mini-App.
- `/panel` : Link langsung `/backoffice` & `/backoffice/settings/marketing-tags`.
- `/reindex <domain>` : Live audit kode HTTP, tag verifikasi Google Search Console, sitemap, dan alert admin.
- `/ticket <kendala>` : Auto-assign tiket `#TKT-XXXX` & alert admin `7862805424` serta grup Telegram.
- `/status_ticket <id> <status>` : Status update tiket keluhan.
- `/trustpositif <domain>` : Link cepat ke database TrustPositif & bot `@reqweb_bot`.
- `/list_domains` : List domain aktif & status indexing.
- `/report` : Ringkasan harian sistem.
