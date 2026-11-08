(() => {
  "use strict";
  const config = window.ADMINKU_CONFIG || {};
  const apiBase = String(config.apiBase || "").replace(/\/$/, "");
  const tg = window.Telegram?.WebApp;
  const initData = tg?.initData || "";
  const state = { user: null, permissions: {}, page: "dashboard", cache: {}, busy: false };
  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  const fmtDate = (value) => value ? new Date(Number(value)).toLocaleString("id-ID") : "—";
  const fmt = (value) => Number(value || 0).toLocaleString("id-ID");
  const statusBadge = (s) => {
    const v = String(s || "-");
    const cls = ["ACTIVE", "SELESAI", "ENABLED"].includes(v) ? "green" : ["OPEN", "DIPROSES", "PENDING_DNS", "PENDING_VERIFICATION"].includes(v) ? "yellow" : ["DITOLAK", "REJECTED", "SUSPENDED"].includes(v) ? "red" : "";
    return `<span class="badge ${cls}">${esc(v)}</span>`;
  };
  const setState = (id, message, kind = "") => { const node = $(id); node.className = `state ${kind}`; node.textContent = message; node.classList.remove("hidden"); };
  const hideState = (id) => $(id).classList.add("hidden");
  const notice = (message, kind = "") => { const n = $("notice"); n.textContent = message; n.className = `notice ${kind}`; n.classList.remove("hidden"); };
  const clearNotice = () => $("notice").classList.add("hidden");

  async function api(path, options = {}) {
    if (!apiBase) throw new Error("Alamat Worker API belum diatur pada config.js.");
    const headers = new Headers(options.headers || {});
    headers.set("X-Telegram-Init-Data", initData);
    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    let response;
    try {
      response = await fetch(`${apiBase}${path}`, { ...options, headers, cache: "no-store" });
    } catch (error) {
      throw new Error("Tidak dapat terhubung ke Worker. Periksa URL API, jaringan, dan BACKOFFICE_ORIGIN/CORS.");
    }
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.error || `Permintaan gagal (HTTP ${response.status}).`);
    return payload;
  }

  function setConnection(kind, text) {
    const node = $("connection"); node.className = `connection ${kind}`; node.innerHTML = `<i></i>${esc(text)}`;
  }

  function applyRole() {
    const role = state.user?.role || "MEMBER";
    $("profile-name").textContent = state.user?.displayName || state.user?.username || `Telegram ${state.user?.id || "user"}`;
    $("profile-role").textContent = role;
    $("profile").querySelector(".avatar").textContent = (state.user?.displayName || state.user?.username || "U").slice(0, 1).toUpperCase();
    document.querySelectorAll(".admin-only").forEach((node) => { node.hidden = !state.permissions.canManageUsers && !state.permissions.canReadAudit; });
    document.querySelectorAll(".admin-write").forEach((node) => { node.hidden = !["ADMIN", "STAFF"].includes(role); });
    $("new-ticket").hidden = role === "VIEWER";
    document.querySelectorAll('.nav-item[data-page="dashboard"]').forEach((node) => { node.hidden = !state.permissions.canReadDashboard; });
    document.querySelectorAll('.nav-item[data-page="domains"], .nav-item[data-page="tags"], .nav-item[data-page="gsc"]').forEach((node) => { node.hidden = !["ADMIN", "STAFF", "VIEWER"].includes(role); });
  }

  function showPage(page) {
    const target = $(`page-${page}`);
    if (!target) return;
    const permission = {
      dashboard: state.permissions.canReadDashboard,
      tickets: ["ADMIN", "STAFF", "VIEWER", "MEMBER"].includes(state.user?.role),
      domains: ["ADMIN", "STAFF", "VIEWER"].includes(state.user?.role),
      tags: ["ADMIN", "STAFF", "VIEWER"].includes(state.user?.role),
      gsc: state.permissions.canReadGsc,
      audit: state.permissions.canReadAudit,
      users: state.permissions.canManageUsers
    }[page];
    if (!permission) { notice("Akses modul ini tidak tersedia untuk role akun Anda.", "error"); return; }
    state.page = page;
    document.querySelectorAll(".page").forEach((n) => n.classList.toggle("active", n.id === `page-${page}`));
    document.querySelectorAll(".nav-item").forEach((n) => n.classList.toggle("active", n.dataset.page === page));
    $("page-title").textContent = ({ dashboard: "Dashboard", tickets: "Manajemen Tiket", domains: "Manajemen Domain", tags: "Marketing Tags", gsc: "Google Search Console", audit: "Audit Log", users: "Pengguna & Hak Akses" })[page];
    $("sidebar").classList.remove("open");
    loadPage(page);
  }

  async function loadPage(page) {
    clearNotice();
    try {
      if (page === "dashboard") await loadDashboard();
      if (page === "tickets") await loadTickets();
      if (page === "domains") await loadDomains();
      if (page === "tags") await loadTags();
      if (page === "gsc") await loadGsc();
      if (page === "audit") await loadAudit();
      if (page === "users") await loadUsers();
    } catch (error) {
      const states = { dashboard: "dashboard-state", tickets: "tickets-state", domains: "domains-state", tags: "tags-state", gsc: "gsc-state", audit: "audit-state", users: "users-state" };
      setState(states[page], error.message, "error");
      setConnection("error", "API bermasalah");
    }
  }

  async function loadDashboard() {
    setState("dashboard-state", "Mengambil ringkasan dari D1…");
    const result = await api("/dashboard");
    const tickets = result.tickets || {}; const domains = result.domains || {};
    const tags = result.marketingTags || {}; const gsc = result.gsc || {};
    const cards = [
      ["Total tiket", tickets.total], ["Tiket terbuka", tickets.open], ["Tiket diproses", tickets.inProgress], ["Total domain", domains.total],
      ["Domain aktif", domains.active], ["Catatan GSC", gsc.total], ["Total tag", tags.total], ["Tag aktif", tags.active]
    ];
    $("dashboard-content").innerHTML = `<div class="stats">${cards.map(([label, val]) => `<article class="stat-card"><small>${esc(label)}</small><strong>${fmt(val)}</strong></article>`).join("")}</div><div class="panel-card"><h3>Tiket terbaru</h3>${table(["Kode", "Pelapor", "Kategori", "Prioritas", "Status", "Dibuat"], (result.recentTickets || []).map((r) => [esc(r.ticketCode), esc(r.memberUsername), esc(r.category), esc(r.priority), statusBadge(r.status), esc(fmtDate(r.createdAt))]))}</div><p class="muted">Data diambil ${esc(fmtDate(result.generatedAt))} · sumber: Worker API / D1</p>`;
    hideState("dashboard-state"); $("dashboard-content").classList.remove("hidden"); setConnection("ok", "Terhubung ke D1");
  }

  function table(headers, rows) {
    if (!rows.length) return `<div class="empty-cell">Belum ada data tersimpan.</div>`;
    return `<div class="table-wrap"><table><thead><tr>${headers.map((h) => `<th>${esc(h)}</th>`).join("")}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((v) => `<td>${v}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
  }

  async function loadTickets() {
    setState("tickets-state", "Mengambil data tiket…");
    const params = new URLSearchParams({ page: "1", limit: "50" });
    if ($("ticket-filter").value) params.set("status", $("ticket-filter").value);
    if ($("ticket-search").value.trim()) params.set("q", $("ticket-search").value.trim());
    const result = await api(`/tickets?${params}`); state.cache.tickets = result.data || [];
    const rows = state.cache.tickets.map((r) => [
      `<strong>${esc(r.ticketCode)}</strong>`, esc(r.memberUsername), esc(r.category), esc(r.priority), statusBadge(r.status), esc(fmtDate(r.createdAt)),
      `<button class="table-action" data-action="ticket-detail" data-id="${esc(r.ticketCode)}">Detail</button>${["ADMIN", "STAFF"].includes(state.user.role) ? `<button class="table-action" data-action="ticket-status" data-id="${esc(r.ticketCode)}">Ubah status</button>` : ""}`
    ]);
    $("tickets-table").innerHTML = table(["Kode", "Pelapor", "Kategori", "Prioritas", "Status", "Dibuat", "Aksi"], rows);
    hideState("tickets-state"); setConnection("ok", "Terhubung ke D1");
  }

  async function loadDomains() {
    setState("domains-state", "Mengambil daftar domain…");
    const params = new URLSearchParams({ page: "1", limit: "100" });
    if ($("domain-filter").value) params.set("status", $("domain-filter").value);
    if ($("domain-search").value.trim()) params.set("q", $("domain-search").value.trim());
    const result = await api(`/domains?${params}`); state.cache.domains = result.data || [];
    const rows = state.cache.domains.map((r) => [`<strong>${esc(r.domainName)}</strong>`, esc(r.category), esc(r.targetPath), statusBadge(r.status), statusBadge(r.registrationStatus), esc(fmtDate(r.createdAt)), `<button class="table-action" data-action="domain-detail" data-id="${r.id}">Detail</button>${["ADMIN", "STAFF"].includes(state.user.role) ? `<button class="table-action" data-action="domain-edit" data-id="${r.id}">Ubah</button>` : ""}`]);
    $("domains-table").innerHTML = table(["Domain", "Kategori", "Target", "Status", "Registrasi", "Dibuat", "Aksi"], rows);
    hideState("domains-state"); setConnection("ok", "Terhubung ke D1");
  }

  async function loadTags() {
    setState("tags-state", "Mengambil daftar marketing tags…");
    const result = await api("/marketing-tags?page=1&limit=100"); state.cache.tags = result.data || [];
    const rows = state.cache.tags.map((r) => [`<strong>${esc(r.name)}</strong>`, esc(r.targetDomain), esc(r.tagType), esc(r.placement), statusBadge(Number(r.isActive) ? "ACTIVE" : "DISABLED"), esc(fmtDate(r.updatedAt)), `<button class="table-action" data-action="tag-detail" data-id="${r.id}">Detail</button>${["ADMIN", "STAFF"].includes(state.user.role) ? `<button class="table-action" data-action="tag-edit" data-id="${r.id}">Edit</button>${Number(r.isActive) ? `<button class="table-action danger" data-action="tag-disable" data-id="${r.id}">Nonaktifkan</button>` : ""}` : ""}`]);
    $("tags-table").innerHTML = table(["Nama", "Domain target", "Jenis", "Posisi", "Status", "Diubah", "Aksi"], rows);
    hideState("tags-state"); setConnection("ok", "Terhubung ke D1");
  }

  async function loadGsc() {
    setState("gsc-state", "Mengambil riwayat GSC dari D1…");
    const result = await api("/gsc/logs?page=1&limit=100");
    const rows = (result.data || []).map((r) => [esc(r.gscTrackingId), `<span title="${esc(r.url)}">${esc(r.domain)}</span>`, esc(r.searchEngine), esc(r.httpStatus), statusBadge(r.indexingStatus), esc(fmtDate(r.submittedAt)), `<button class="table-action" data-action="gsc-detail" data-id="${r.id}">Detail</button>`]);
    $("gsc-table").innerHTML = table(["Tracking ID", "Domain", "Sumber", "HTTP", "Status catatan", "Waktu", "Aksi"], rows);
    hideState("gsc-state"); setConnection("ok", "Terhubung ke D1");
  }

  async function loadAudit() {
    setState("audit-state", "Mengambil audit log…");
    const result = await api("/audit?page=1&limit=100");
    const rows = (result.data || []).map((r) => [esc(fmtDate(r.createdAt)), esc(r.actorUsername || r.actorTelegramUserId), esc(r.action), esc(r.resourceType), esc(r.resourceId), `<button class="table-action" data-action="audit-detail" data-id="${r.id}">Lihat perubahan</button>`]);
    $("audit-table").innerHTML = table(["Waktu", "Aktor", "Aksi", "Entitas", "ID", "Detail"], rows);
    state.cache.audit = result.data || []; hideState("audit-state"); setConnection("ok", "Terhubung ke D1");
  }

  async function loadUsers() {
    setState("users-state", "Mengambil daftar pengguna…");
    const result = await api("/users"); state.cache.users = result.data || [];
    const rows = state.cache.users.map((r) => [esc(r.telegramUserId), esc([r.username ? `@${r.username}` : "", r.firstName, r.lastName].filter(Boolean).join(" · ")), statusBadge(r.role), statusBadge(Number(r.isActive) ? "ACTIVE" : "DISABLED"), esc(fmtDate(r.updatedAt))]);
    $("users-table").innerHTML = table(["Telegram ID", "Pengguna", "Role", "Status", "Terakhir diubah"], rows);
    hideState("users-state"); setConnection("ok", "Terhubung ke D1");
  }

  function openEditor(title, fields, submit) {
    $("editor-title").textContent = title;
    $("editor-fields").innerHTML = fields.map((f) => {
      const full = f.full ? " full" : "";
      const val = esc(f.value ?? "");
      let control;
      if (f.options) control = `<select class="field-input" name="${esc(f.name)}" ${f.required ? "required" : ""}>${f.options.map((o) => `<option value="${esc(o.value)}" ${String(o.value) === String(f.value) ? "selected" : ""}>${esc(o.label)}</option>`).join("")}</select>`;
      else if (f.type === "textarea") control = `<textarea class="field-input textarea" name="${esc(f.name)}" maxlength="${f.max || 2000}" ${f.required ? "required" : ""}>${val}</textarea>`;
      else control = `<input class="field-input" name="${esc(f.name)}" type="${esc(f.type || "text")}" value="${val}" maxlength="${f.max || 253}" ${f.required ? "required" : ""}>`;
      return `<div class="form-field${full}"><label>${esc(f.label)}</label>${control}</div>`;
    }).join("");
    const form = $("editor-form");
    form.onsubmit = async (event) => {
      event.preventDefault();
      const button = $("editor-submit"); button.disabled = true; button.textContent = "Menyimpan…";
      try {
        const data = Object.fromEntries(new FormData(form).entries());
        await submit(data); $("editor").close(); notice("Perubahan berhasil disimpan.", "success"); await loadPage(state.page);
      } catch (error) { notice(error.message, "error"); }
      finally { button.disabled = false; button.textContent = "Simpan"; }
    };
    $("editor").showModal();
  }

  function openDetail(title, pairs) {
    $("detail-title").textContent = title;
    $("detail-content").innerHTML = `<div class="detail-list">${pairs.map(([k, v]) => `<div class="detail-row"><span>${esc(k)}</span><div>${v}</div></div>`).join("")}</div>`;
    $("detail").showModal();
  }

  function bindEvents() {
    document.querySelectorAll(".nav-item").forEach((node) => node.addEventListener("click", () => showPage(node.dataset.page)));
    $("mobile-menu").addEventListener("click", () => $("sidebar").classList.toggle("open"));
    $("refresh-btn").addEventListener("click", () => loadPage(state.page));
    $("gsc-refresh").addEventListener("click", () => loadGsc());
    $("detail-close").addEventListener("click", () => $("detail").close());
    $("ticket-filter").addEventListener("change", loadTickets);
    $("domain-filter").addEventListener("change", loadDomains);
    let searchTimer;
    $("ticket-search").addEventListener("input", () => { clearTimeout(searchTimer); searchTimer = setTimeout(loadTickets, 300); });
    $("domain-search").addEventListener("input", () => { clearTimeout(searchTimer); searchTimer = setTimeout(loadDomains, 300); });
    $("new-ticket").addEventListener("click", () => openEditor("Buat tiket", [
      { name: "category", label: "Kategori", value: "Lainnya", max: 60 },
      { name: "priority", label: "Prioritas", value: "SEDANG", options: ["RENDAH", "SEDANG", "TINGGI", "KRITIS"].map((x) => ({ value: x, label: x })) },
      { name: "domainAffected", label: "Domain terkait", max: 253 },
      { name: "issueDescription", label: "Deskripsi kendala", type: "textarea", full: true, required: true, max: 2000 }
    ], (data) => api("/tickets", { method: "POST", body: JSON.stringify(data) })));
    $("new-domain").addEventListener("click", () => openEditor("Tambah domain", [
      { name: "domainName", label: "Nama domain", required: true }, { name: "category", label: "Kategori", value: "Website", required: true },
      { name: "targetPath", label: "Target path", value: "/backoffice" }, { name: "notes", label: "Catatan", type: "textarea", full: true }
    ], (data) => api("/domains", { method: "POST", body: JSON.stringify(data) })));
    $("new-tag").addEventListener("click", () => openEditor("Tambah marketing tag", [
      { name: "name", label: "Nama tag", required: true }, { name: "tagId", label: "ID tag / kode tracking", required: true },
      { name: "targetDomain", label: "Domain target (* untuk semua domain)", value: "*" },
      { name: "targetPath", label: "Path target", value: "/" },
      { name: "tagType", label: "Jenis", value: "SCRIPT", options: ["SCRIPT", "NOSCRIPT", "META", "PIXEL", "HTML"].map((x) => ({ value: x, label: x })) },
      { name: "placement", label: "Posisi", value: "HEAD", options: ["HEAD", "BODY_START", "BODY_END"].map((x) => ({ value: x, label: x })) },
      { name: "notes", label: "Catatan", type: "textarea", full: true }
    ], (data) => { data.isActive = true; return api("/marketing-tags", { method: "POST", body: JSON.stringify(data) }); }));
    $("new-user").addEventListener("click", () => openEditor("Atur role pengguna", [
      { name: "telegramUserId", label: "Telegram user ID", required: true, max: 24 },
      { name: "username", label: "Username Telegram" }, { name: "firstName", label: "Nama depan" },
      { name: "lastName", label: "Nama belakang" },
      { name: "role", label: "Role", value: "VIEWER", options: ["STAFF", "VIEWER", "MEMBER"].map((x) => ({ value: x, label: x })) },
      { name: "notes", label: "Catatan" }
    ], (data) => api("/users", { method: "POST", body: JSON.stringify(data) })));

    document.body.addEventListener("click", async (event) => {
      const button = event.target.closest("[data-action]"); if (!button) return;
      const action = button.dataset.action; const id = button.dataset.id;
      try {
        if (action === "ticket-detail") {
          const r = await api(`/tickets/${encodeURIComponent(id)}`); const d = r.data;
          openDetail(`Tiket ${d.ticketCode}`, [["Pelapor", esc(d.memberUsername)], ["Status", statusBadge(d.status)], ["Kategori", esc(d.category)], ["Prioritas", esc(d.priority)], ["Kendala", esc(d.issueDescription)], ["Catatan admin", esc(d.adminNotes || "—")], ["Riwayat", (r.history || []).map((h) => `${esc(fmtDate(h.createdAt))} · ${esc(h.oldStatus)} → ${esc(h.newStatus)} · ${esc(h.note)}`).join("<br>") || "—"]]);
        }
        if (action === "ticket-status") {
          const current = state.cache.tickets.find((x) => x.ticketCode === id);
          openEditor(`Ubah tiket ${id}`, [
            { name: "status", label: "Status", value: current?.status || "OPEN", options: ["OPEN", "DIPROSES", "SELESAI", "DITOLAK"].map((x) => ({ value: x, label: x })) },
            { name: "priority", label: "Prioritas", value: current?.priority || "SEDANG", options: ["RENDAH", "SEDANG", "TINGGI", "KRITIS"].map((x) => ({ value: x, label: x })) },
            { name: "category", label: "Kategori", value: current?.category || "Lainnya" },
            { name: "adminNotes", label: "Catatan admin", type: "textarea", full: true, value: current?.adminNotes || "" }
          ], (data) => api(`/tickets/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify(data) }));
        }
        if (action === "domain-detail" || action === "domain-edit") {
          const current = state.cache.domains.find((x) => String(x.id) === String(id));
          if (action === "domain-detail") openDetail(`Domain ${current?.domainName || id}`, Object.entries(current || {}).map(([k, v]) => [k, esc(v)]));
          else openEditor(`Ubah domain ${current?.domainName || id}`, [
            { name: "status", label: "Status", value: current?.status, options: ["PENDING_DNS", "PENDING_VERIFICATION", "ACTIVE", "REJECTED", "SUSPENDED"].map((x) => ({ value: x, label: x })) },
            { name: "registrationStatus", label: "Registrasi", value: current?.registrationStatus || "PENDING" },
            { name: "category", label: "Kategori", value: current?.category || "" }, { name: "targetPath", label: "Target path", value: current?.targetPath || "/backoffice" },
            { name: "dnsSettings", label: "DNS settings", value: current?.dnsSettings || "" }, { name: "gscIndexingStatus", label: "Status GSC", value: current?.gscIndexingStatus || "" },
            { name: "notes", label: "Catatan", type: "textarea", full: true, value: current?.notes || "" }, { name: "rejectionReason", label: "Alasan penolakan", type: "textarea", full: true, value: current?.rejectionReason || "" }
          ], (data) => api(`/domains/${id}`, { method: "PATCH", body: JSON.stringify(data) }));
        }
        if (action === "tag-detail" || action === "tag-edit") {
          const current = state.cache.tags.find((x) => String(x.id) === String(id));
          if (action === "tag-detail") openDetail(`Marketing tag ${current?.name || id}`, Object.entries(current || {}).map(([k, v]) => [k, esc(v)]));
          else openEditor(`Edit marketing tag`, [
            { name: "name", label: "Nama", value: current?.name }, { name: "tagId", label: "ID tag / kode tracking", value: current?.tagId, required: true },
            { name: "targetDomain", label: "Domain target", value: current?.targetDomain || "*" }, { name: "targetPath", label: "Path target", value: current?.targetPath || "/" },
            { name: "tagType", label: "Jenis", value: current?.tagType, options: ["SCRIPT", "NOSCRIPT", "META", "PIXEL", "HTML"].map((x) => ({ value: x, label: x })) },
            { name: "placement", label: "Posisi", value: current?.placement, options: ["HEAD", "BODY_START", "BODY_END"].map((x) => ({ value: x, label: x })) },
            { name: "isActive", label: "Status", value: Number(current?.isActive) ? "true" : "false", options: [{ value: "true", label: "Aktif" }, { value: "false", label: "Nonaktif" }] },
            { name: "notes", label: "Catatan", type: "textarea", value: current?.notes, full: true }
          ], (data) => { data.isActive = data.isActive === "true"; return api(`/marketing-tags/${id}`, { method: "PATCH", body: JSON.stringify(data) }); });
        }
        if (action === "tag-disable" && confirm("Nonaktifkan marketing tag ini?")) await api(`/marketing-tags/${id}`, { method: "DELETE" }).then(() => { notice("Tag dinonaktifkan.", "success"); loadTags(); });
        if (action === "gsc-detail") {
          const rows = await api(`/gsc/logs?page=1&limit=100`); const item = (rows.data || []).find((x) => String(x.id) === String(id));
          if (item) openDetail(`GSC ${item.gscTrackingId}`, Object.entries(item).map(([k, v]) => [k, k === "diagnosticMessage" ? `<pre class="code">${esc(v)}</pre>` : esc(v)]));
        }
        if (action === "audit-detail") {
          const item = (state.cache.audit || []).find((x) => String(x.id) === String(id));
          if (item) openDetail(`Audit #${item.id}`, [["Waktu", esc(fmtDate(item.createdAt))], ["Aktor", esc(item.actorUsername || item.actorTelegramUserId)], ["Role", esc(item.actorRole)], ["Aksi", esc(item.action)], ["Entitas", esc(`${item.resourceType} / ${item.resourceId}`)], ["Detail", `<pre class="code">${esc(item.detailsJson || "—")}</pre>`]]);
        }
      } catch (error) { notice(error.message, "error"); }
    });
  }

  async function start() {
    bindEvents();
    if (tg) { tg.ready(); tg.expand(); }
    $("reqweb-link").href = config.reqWebUrl || "https://t.me/reqweb_bot";
    if (!initData) {
      setConnection("error", "Belum ada sesi Telegram");
      setState("dashboard-state", "Web App ini hanya menerima login dari Telegram Mini App. Buka melalui tombol Web App pada bot Adminku; initData mentah diperlukan untuk autentikasi server.", "error");
      return;
    }
    if (!apiBase) { setConnection("error", "API belum dikonfigurasi"); setState("dashboard-state", "Alamat Worker API belum diatur.", "error"); return; }
    try {
      const me = await api("/auth/me"); state.user = me.user; state.permissions = me.permissions || {};
      applyRole(); setConnection("ok", "Sesi Telegram tervalidasi");
      const canSeeDashboard = state.permissions.canReadDashboard;
      showPage(canSeeDashboard ? "dashboard" : "tickets");
    } catch (error) {
      setConnection("error", "Autentikasi/API gagal");
      setState("dashboard-state", error.message, "error");
    }
  }
  start();
})();
