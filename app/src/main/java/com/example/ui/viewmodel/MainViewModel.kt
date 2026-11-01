package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.DomainRequest
import com.example.data.model.GscSubmissionLog
import com.example.data.model.MemberTicket
import com.example.data.network.ChatMessage
import com.example.data.network.GeminiApiService
import com.example.data.network.CloudflareDeployService
import com.example.data.network.GscInspectionResult
import com.example.data.network.GscService
import com.example.data.network.ReindexExecutionResult
import com.example.data.network.TelegramApiService
import com.example.data.network.TelegramMessageService
import com.example.data.network.TelegramSendResult
import com.example.data.prefs.AppSettings
import com.example.data.prefs.SettingsRepository
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface TelegramUiStatus {
    data object Idle : TelegramUiStatus
    data object Sending : TelegramUiStatus
    data class Success(val message: String, val messageId: Long) : TelegramUiStatus
    data class Error(val errorMessage: String) : TelegramUiStatus
}

sealed interface ReindexUiStatus {
    data object Idle : ReindexUiStatus
    data class Loading(val domain: String) : ReindexUiStatus
    data class Result(val executionResult: ReindexExecutionResult) : ReindexUiStatus
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val domainDao = db.domainDao()
    private val ticketDao = db.ticketDao()
    private val gscSubmissionDao = db.gscSubmissionDao()
    private val settingsRepo = SettingsRepository(application)
    private val telegramApi = TelegramApiService()
    private val telegramMessageService = TelegramMessageService()
    private val geminiApi = GeminiApiService()
    private val geminiRepository = GeminiRepository(geminiApi)
    private val gscService = GscService()
    private val cloudflareDeployService = CloudflareDeployService()

    val domains: StateFlow<List<DomainRequest>> = domainDao.getAllDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tickets: StateFlow<List<MemberTicket>> = ticketDao.getAllTickets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gscSubmissionLogs: StateFlow<List<GscSubmissionLog>> = gscSubmissionDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settings

    private val _telegramStatus = MutableStateFlow<TelegramUiStatus>(TelegramUiStatus.Idle)
    val telegramStatus: StateFlow<TelegramUiStatus> = _telegramStatus.asStateFlow()

    private val _reindexStatus = MutableStateFlow<ReindexUiStatus>(ReindexUiStatus.Idle)
    val reindexStatus: StateFlow<ReindexUiStatus> = _reindexStatus.asStateFlow()

    private val _botVerificationResult = MutableStateFlow<String?>(null)
    val botVerificationResult: StateFlow<String?> = _botVerificationResult.asStateFlow()

    private val _botCommandDeployResult = MutableStateFlow<String?>(null)
    val botCommandDeployResult: StateFlow<String?> = _botCommandDeployResult.asStateFlow()

    private val _webhookInfoResult = MutableStateFlow<String?>(null)
    val webhookInfoResult: StateFlow<String?> = _webhookInfoResult.asStateFlow()

    private val _webhookActionLoading = MutableStateFlow(false)
    val webhookActionLoading: StateFlow<Boolean> = _webhookActionLoading.asStateFlow()

    private val _ticketAiSuggestion = MutableStateFlow<String?>(null)
    val ticketAiSuggestion: StateFlow<String?> = _ticketAiSuggestion.asStateFlow()

    private val _isTicketAiLoading = MutableStateFlow(false)
    val isTicketAiLoading: StateFlow<Boolean> = _isTicketAiLoading.asStateFlow()

    private val _cloudflareDeployStatus = MutableStateFlow<String?>(null)
    val cloudflareDeployStatus: StateFlow<String?> = _cloudflareDeployStatus.asStateFlow()

    private val _isCloudflareDeploying = MutableStateFlow(false)
    val isCloudflareDeploying: StateFlow<Boolean> = _isCloudflareDeploying.asStateFlow()

    private val _isPollerRunning = MutableStateFlow(false)
    val isPollerRunning: StateFlow<Boolean> = _isPollerRunning.asStateFlow()

    private val _botLogs = MutableStateFlow<List<String>>(listOf("System Ready. Bot Engine initialized."))
    val botLogs: StateFlow<List<String>> = _botLogs.asStateFlow()

    private var pollerJob: Job? = null
    private var lastUpdateId: Long = 0L

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                role = "model",
                content = "Halo Admin! Asisten Cerdas Backoffice, GSC & Telegram siap membantu. Anda dapat memicu re-index domain Google Search Console (/backoffice/settings/marketing-tags), memantau tiket keluhan member, mendaftarkan perintah bot resmi, atau menyusun laporan otomatis untuk dikirim ke Telegram (Admin 7862805424 & Grup @felixsnd)."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun logBot(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _botLogs.value = (listOf("[$time] $message") + _botLogs.value).take(50)
    }

    // ==========================================
    // RE-INDEXING GOOGLE SEARCH CONSOLE & MARKETING TAGS
    // ==========================================
    fun triggerDomainReindex(domain: String) {
        viewModelScope.launch {
            val cleanDomain = domain.replace("https://", "").replace("http://", "").trim()
            _reindexStatus.value = ReindexUiStatus.Loading(cleanDomain)
            logBot("Memulai proses audit & re-indexing GSC untuk $cleanDomain...")

            val result = gscService.triggerReindexing(
                domain = cleanDomain,
                marketingTagsPath = settings.value.marketingTagsPath,
                adminId = settings.value.telegramAdminId
            )

            // Update local database domain status if exists, or insert new record
            val existingDomain = domainDao.getDomainByName(cleanDomain)
            if (existingDomain != null) {
                domainDao.updateDomain(
                    existingDomain.copy(
                        gscIndexingStatus = if (result.success) DomainRequest.GSC_SUBMITTED else DomainRequest.GSC_NOT_INDEXED,
                        status = if (result.marketingTagsVerified) DomainRequest.STATUS_INDEXED else existingDomain.status,
                        lastReindexedAt = System.currentTimeMillis()
                    )
                )
            } else {
                domainDao.insertDomain(
                    DomainRequest(
                        domainName = cleanDomain,
                        category = "Landing Utama",
                        targetPath = settings.value.marketingTagsPath,
                        status = if (result.marketingTagsVerified) DomainRequest.STATUS_INDEXED else DomainRequest.STATUS_PENDING,
                        gscIndexingStatus = if (result.success) DomainRequest.GSC_SUBMITTED else DomainRequest.GSC_NOT_INDEXED,
                        lastReindexedAt = System.currentTimeMillis(),
                        notes = "Diminta Re-index via panel marketing-tags"
                    )
                )
            }

            // Report success or failure to Admin (7862805424) and Admin Group
            val token = settings.value.telegramBotToken
            if (token.isNotBlank()) {
                logBot("Mengirim laporan re-index ke Admin (${settings.value.telegramAdminId}) & Grup...")
                telegramApi.sendMessage(token, settings.value.telegramAdminId, result.reportText)
                if (settings.value.telegramGroupId.isNotBlank()) {
                    telegramApi.sendMessage(token, settings.value.telegramGroupId, result.reportText)
                }
            } else {
                logBot("Token Bot Telegram belum diatur. Laporan re-index tersimpan lokal.")
            }

            _reindexStatus.value = ReindexUiStatus.Result(result)
            logBot("Re-index $cleanDomain selesai: ${if (result.success) "SUKSES" else "GAGAL"}")
        }
    }

    fun dismissReindexStatus() {
        _reindexStatus.value = ReindexUiStatus.Idle
    }

    // ==========================================
    // TICKET MANAGEMENT SYSTEM WITH UNIQUE ID & ALERTS
    // ==========================================
    fun createMemberTicket(
        memberUsername: String,
        category: String,
        priority: String,
        domainAffected: String,
        issueDescription: String,
        adminNotes: String,
        reportedVia: String = "MANUAL"
    ) {
        viewModelScope.launch {
            val ticketCode = MemberTicket.generateTicketCode()
            val ticket = MemberTicket(
                ticketCode = ticketCode,
                memberUsername = memberUsername.trim(),
                category = category,
                priority = priority,
                domainAffected = domainAffected.trim(),
                issueDescription = issueDescription.trim(),
                status = MemberTicket.STATUS_OPEN,
                reportedVia = reportedVia,
                adminNotes = adminNotes.trim(),
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )
            ticketDao.insertTicket(ticket)
            logBot("Tiket baru dibuat: #${ticket.ticketCode} (${ticket.memberUsername})")

            // Automatically dispatch notification via Retrofit TelegramMessageService
            val token = settings.value.telegramBotToken
            if (token.isNotBlank()) {
                telegramMessageService.dispatchTicketCreated(
                    botToken = token,
                    ticket = ticket,
                    adminId = settings.value.telegramAdminId,
                    groupId = settings.value.telegramGroupId,
                    webAppUrl = settings.value.telegramWebAppUrl
                )
                logBot("Notifikasi tiket #${ticket.ticketCode} terkirim via TelegramMessageService ke Admin & Grup.")
            }
        }
    }

    fun updateTicketStatus(ticket: MemberTicket, newStatus: String, notes: String = ticket.adminNotes) {
        viewModelScope.launch {
            val oldStatus = ticket.status
            val updated = ticket.copy(
                status = newStatus,
                adminNotes = notes,
                lastUpdated = System.currentTimeMillis()
            )
            ticketDao.updateTicket(updated)
            logBot("Status tiket #${ticket.ticketCode} diperbarui: $oldStatus -> $newStatus")

            // Dispatch update notification via Retrofit TelegramMessageService
            val token = settings.value.telegramBotToken
            if (token.isNotBlank()) {
                telegramMessageService.dispatchTicketStatusUpdated(
                    botToken = token,
                    ticket = updated,
                    oldStatus = oldStatus,
                    newStatus = newStatus,
                    adminId = settings.value.telegramAdminId,
                    groupId = settings.value.telegramGroupId
                )
                logBot("Update tiket #${ticket.ticketCode} disiarkan ke Telegram.")
            }
        }
    }

    fun requestAiSuggestionForTicket(ticket: MemberTicket) {
        viewModelScope.launch {
            _isTicketAiLoading.value = true
            _ticketAiSuggestion.value = null
            logBot("Meminta analisis cerdas Gemini AI untuk #${ticket.ticketCode}...")
            val apiKey = settingsRepo.getEffectiveGeminiApiKey()
            val result = geminiRepository.generateResponseSuggestion(ticket, apiKey)
            _isTicketAiLoading.value = false
            result.onSuccess { suggestion ->
                _ticketAiSuggestion.value = suggestion
                logBot("Gemini AI berhasil menghasilkan saran solusi untuk #${ticket.ticketCode}.")
            }.onFailure { err ->
                _ticketAiSuggestion.value = "⚠️ Gagal menghasilkan saran AI: ${err.message}"
                logBot("Error Gemini AI: ${err.message}")
            }
        }
    }

    fun clearTicketAiSuggestion() {
        _ticketAiSuggestion.value = null
    }

    fun dispatchAiSuggestionToTelegram(ticket: MemberTicket, suggestion: String) {
        viewModelScope.launch {
            val token = settings.value.telegramBotToken
            val adminId = settings.value.telegramAdminId
            val groupId = settings.value.telegramGroupId
            val res = telegramMessageService.dispatchAiSuggestedReply(token, ticket, suggestion, adminId, groupId)
            res.onSuccess {
                logBot("Draft saran AI untuk #${ticket.ticketCode} berhasil dikirim ke Telegram!")
            }.onFailure {
                logBot("Gagal kirim draft AI ke Telegram: ${it.message}")
            }
        }
    }

    fun submitUrlForIndexing(rawUrl: String, searchEngine: String = "Google Search Console") {
        viewModelScope.launch {
            val cleanUrl = rawUrl.trim()
            if (cleanUrl.isEmpty()) return@launch
            val domain = cleanUrl.replace("https://", "").replace("http://", "").split("/")[0]
            _reindexStatus.value = ReindexUiStatus.Loading(domain)
            logBot("Mengajukan URL indexing [$searchEngine]: $cleanUrl")

            val result = gscService.triggerReindexing(
                domain = domain,
                marketingTagsPath = settings.value.marketingTagsPath,
                adminId = settings.value.telegramAdminId
            )
            _reindexStatus.value = ReindexUiStatus.Result(result)

            val fullUrl = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) cleanUrl else "https://$cleanUrl"
            val inspection = gscService.inspectDomainGsc(domain)

            // Persist into Room database GscSubmissionLog
            val logEntry = GscSubmissionLog(
                url = fullUrl,
                domain = domain,
                searchEngine = searchEngine,
                httpStatus = inspection.httpCode,
                verificationTagFound = inspection.hasVerificationTag,
                sitemapDetected = inspection.hasSitemap,
                indexingStatus = inspection.indexingStatus,
                diagnosticMessage = result.message,
                gscTrackingId = result.gscTrackingId,
                submittedAt = System.currentTimeMillis()
            )
            gscSubmissionDao.insertLog(logEntry)

            val token = settings.value.telegramBotToken
            if (token.isNotEmpty()) {
                val adminId = settings.value.telegramAdminId
                telegramMessageService.sendMessage(
                    botToken = token,
                    chatId = adminId,
                    text = """
                        🔍 <b>[SUBMIT URL INDEXING LANGSUNG]</b>
                        ━━━━━━━━━━━━━━━━━━━━
                        • <b>Mesin Pencari:</b> $searchEngine
                        • <b>URL Target:</b> <code>$fullUrl</code>
                        • <b>Domain:</b> <code>$domain</code>
                        • <b>Tracking Code:</b> <code>#${result.gscTrackingId}</code>
                        • <b>Status Audit:</b> <code>${inspection.indexingStatus}</code>
                        • <b>Meta Tag GSC:</b> ${if (inspection.hasVerificationTag) "✅ TERVERIFIKASI" else "⚠️ BELUM TERDETEKSI"}
                        • <b>Sitemap.xml:</b> ${if (inspection.hasSitemap) "✅ TERSEDIA" else "⚪ BELUM TERDETEKSI"}
                        • <b>Rincian:</b> ${result.message}
                        ━━━━━━━━━━━━━━━━━━━━
                        🤖 <i>Tercatat di Riwayat Log GSC Backoffice</i>
                    """.trimIndent()
                )
            }
            logBot("URL $fullUrl berhasil disimpan ke Riwayat Log GSC (#${result.gscTrackingId})")
        }
    }

    fun deleteGscLog(log: GscSubmissionLog) {
        viewModelScope.launch {
            gscSubmissionDao.deleteLog(log)
            logBot("Log riwayat GSC #${log.gscTrackingId} dihapus.")
        }
    }

    fun clearAllGscLogs() {
        viewModelScope.launch {
            gscSubmissionDao.clearAllLogs()
            logBot("Seluruh riwayat log indexing GSC berhasil dibersihkan.")
        }
    }

    fun approveDomainRequest(domain: DomainRequest) {
        viewModelScope.launch {
            val updated = domain.copy(
                registrationStatus = DomainRequest.REG_APPROVED,
                status = DomainRequest.STATUS_ACTIVE
            )
            domainDao.updateDomain(updated)
            logBot("Domain ${domain.domainName} DISETUJUI.")

            val token = settings.value.telegramBotToken
            if (token.isNotEmpty()) {
                val adminId = settings.value.telegramAdminId
                telegramMessageService.sendMessage(
                    botToken = token,
                    chatId = adminId,
                    text = "✅ <b>[PERMOHONAN DOMAIN DISETUJUI]</b>\nDomain: <code>${domain.domainName}</code>\nStatus: <b>AKTIF</b>\nAdmin dapat melanjutkan konfigurasi DNS dan GSC."
                )
            }
        }
    }

    fun rejectDomainRequest(domain: DomainRequest, reason: String) {
        viewModelScope.launch {
            val updated = domain.copy(
                registrationStatus = DomainRequest.REG_REJECTED,
                rejectionReason = reason
            )
            domainDao.updateDomain(updated)
            logBot("Domain ${domain.domainName} DITOLAK. Alasan: $reason")

            val token = settings.value.telegramBotToken
            if (token.isNotEmpty()) {
                val adminId = settings.value.telegramAdminId
                telegramMessageService.sendMessage(
                    botToken = token,
                    chatId = adminId,
                    text = "❌ <b>[PERMOHONAN DOMAIN DITOLAK]</b>\nDomain: <code>${domain.domainName}</code>\nAlasan: $reason"
                )
            }
        }
    }

    fun deployRemoteToCloudflare(apiToken: String, accountId: String, subdomain: String = "") {
        viewModelScope.launch {
            _isCloudflareDeploying.value = true
            _cloudflareDeployStatus.value = null
            logBot("Memulai remote deployment script worker ke Cloudflare API...")

            val workerScript = """
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/status")) {
      return new Response(JSON.stringify({ status: "online", platform: "Cloudflare Workers", time: new Date() }), {
        headers: { "Content-Type": "application/json" }
      });
    }
    if (request.method === "POST" && (url.pathname === "/webhook" || url.pathname === "/")) {
      return new Response("OK", { status: 200 });
    }
    return new Response("Not Found", { status: 404 });
  }
};
            """.trimIndent()

            val deployResult = cloudflareDeployService.deployRemoteWorker(
                apiToken = apiToken,
                accountId = accountId,
                workerCode = workerScript,
                subdomain = subdomain
            )
            _isCloudflareDeploying.value = false
            _cloudflareDeployStatus.value = deployResult.message
            logBot("Hasil remote deploy Cloudflare: ${deployResult.message}")

            if (deployResult.success && deployResult.workerUrl != null) {
                setTelegramWebhook(deployResult.workerUrl)
            }
        }
    }

    fun deleteTicket(ticket: MemberTicket) {
        viewModelScope.launch {
            ticketDao.deleteTicket(ticket)
            logBot("Tiket #${ticket.ticketCode} dihapus.")
        }
    }

    // ==========================================
    // DOMAIN REQUEST MANAGEMENT
    // ==========================================
    fun addDomain(domainName: String, category: String, targetPath: String, dnsSettings: String, notes: String) {
        viewModelScope.launch {
            val cleanName = domainName.replace("https://", "").replace("http://", "").trim()
            domainDao.insertDomain(
                DomainRequest(
                    domainName = cleanName,
                    category = category,
                    targetPath = targetPath,
                    status = DomainRequest.STATUS_PENDING,
                    dnsSettings = dnsSettings.trim(),
                    notes = notes.trim()
                )
            )
            logBot("Domain baru didaftarkan: $cleanName")
        }
    }

    fun updateDomainStatus(domain: DomainRequest, newStatus: String) {
        viewModelScope.launch {
            domainDao.updateDomain(domain.copy(status = newStatus))
            logBot("Status domain ${domain.domainName} diubah ke $newStatus")
        }
    }

    fun deleteDomain(domain: DomainRequest) {
        viewModelScope.launch {
            domainDao.deleteDomain(domain)
            logBot("Domain ${domain.domainName} dihapus.")
        }
    }

    // ==========================================
    // GOOGLE SEARCH CONSOLE AUDIT & REPORT
    // ==========================================
    fun generateGscSummaryReport(): String {
        return gscService.buildGscDomainsSummaryReport(
            domains = domains.value,
            adminId = settings.value.telegramAdminId,
            groupLink = settings.value.telegramGroupLink
        )
    }

    fun sendGscReportToTelegram() {
        val text = generateGscSummaryReport()
        sendTelegramReport(settings.value.telegramAdminId, text)
        if (settings.value.telegramGroupId.isNotBlank()) {
            sendTelegramReport(settings.value.telegramGroupId, text)
        }
    }

    // ==========================================
    // TELEGRAM BOT COMMANDS & WEB APP INTEGRATION
    // ==========================================
    fun deployBotCommands() {
        val token = settings.value.telegramBotToken
        viewModelScope.launch {
            _botCommandDeployResult.value = "Mendaftarkan commands ke Telegram..."
            val result = telegramApi.registerBotCommands(token)
            result.onSuccess { msg ->
                _botCommandDeployResult.value = "✅ $msg"
                logBot("Perintah bot resmi berhasil didaftarkan di @BotFather server.")
            }.onFailure { err ->
                _botCommandDeployResult.value = "❌ ${err.message}"
                logBot("Gagal mendaftarkan bot commands: ${err.message}")
            }
        }
    }

    fun deployWebAppButton() {
        val token = settings.value.telegramBotToken
        val webAppUrl = settings.value.telegramWebAppUrl
        viewModelScope.launch {
            _botCommandDeployResult.value = "Mengatur Tombol Web App Menu..."
            val res = telegramApi.setChatMenuButton(token, webAppUrl)
            res.onSuccess {
                _botCommandDeployResult.value = "✅ Menu Web App Berhasil Dikonfigurasi!"
                logBot("Tombol Menu Web App aktif menuju: $webAppUrl")
            }.onFailure { err ->
                _botCommandDeployResult.value = "❌ ${err.message}"
            }

            // Also send an invitation card with Web App button to Admin
            val msg = """
                🌐 <b>PANEL BACKOFFICE & MARKETING TAGS SUITE</b>
                ━━━━━━━━━━━━━━━━━━━━
                Gunakan tombol di bawah untuk membuka antarmuka panel, mengelola request domain, kendala member, dan index ulang GSC langsung di Telegram!
            """.trimIndent()
            telegramApi.sendWebAppInvitation(token, settings.value.telegramAdminId, msg, webAppUrl)
        }
    }

    fun toggleBotPoller() {
        if (_isPollerRunning.value) {
            stopBotPoller()
        } else {
            startBotPoller()
        }
    }

    private fun startBotPoller() {
        val token = settings.value.telegramBotToken
        if (token.isBlank()) {
            logBot("⚠️ Tidak dapat memulai Poller: Token Bot kosong.")
            return
        }

        _isPollerRunning.value = true
        logBot("🟢 Telegram Bot Poller aktif! Mendengarkan perintah Telegram...")

        pollerJob = viewModelScope.launch {
            while (isActive && _isPollerRunning.value) {
                try {
                    val result = telegramApi.getUpdates(token, offset = lastUpdateId + 1, limit = 5)
                    result.onSuccess { updates ->
                        for (upd in updates) {
                            lastUpdateId = upd.updateId
                            handleIncomingBotMessage(upd)
                        }
                    }.onFailure {
                        // ignore network transient failures
                    }
                } catch (e: Exception) {
                    // ignore
                }
                delay(3000)
            }
        }
    }

    private fun stopBotPoller() {
        _isPollerRunning.value = false
        pollerJob?.cancel()
        pollerJob = null
        logBot("🔴 Telegram Bot Poller dinonaktifkan.")
    }

    private suspend fun handleIncomingBotMessage(update: com.example.data.network.TelegramIncomingUpdate) {
        val token = settings.value.telegramBotToken
        val text = update.text.trim()
        val chatId = update.chatId
        val username = update.fromUsername

        logBot("Pesan masuk dari @$username: $text")

        when {
            text.startsWith("/start") -> {
                val reply = """
                    👋 <b>Halo $username! Selamat datang di Backoffice & SEO Bot.</b>
                    ━━━━━━━━━━━━━━━━━━━━
                    🤖 <b>Daftar Perintah Tersedia:</b>
                    • /panel - Akses panel /backoffice & marketing tags
                    • /reindex &lt;domain&gt; - Trigger index ulang Google Search Console
                    • /ticket &lt;kendala&gt; - Buat tiket keluhan member
                    • /status_ticket &lt;id&gt; &lt;status&gt; - Update status tiket
                    • /gsc_inspect &lt;domain&gt; - Cek status index GSC
                    • /list_domains - Lihat daftar domain
                    • /report - Laporan lengkap
                """.trimIndent()
                telegramApi.sendWebAppInvitation(token, chatId, reply, settings.value.telegramWebAppUrl, "🌐 Buka Panel Backoffice Web App")
            }

            text.startsWith("/panel") -> {
                val base = settings.value.baseDomain
                val reply = """
                    🌐 <b>AKSES PANEL BACKOFFICE</b>
                    ━━━━━━━━━━━━━━━━━━━━
                    • <b>Panel Utama:</b> <code>https://$base/backoffice</code>
                    • <b>Marketing Tags:</b> <code>https://$base/backoffice/settings/marketing-tags</code>
                    • <b>Google Search Console:</b> https://search.google.com/search-console/welcome
                """.trimIndent()
                telegramApi.sendWebAppInvitation(token, chatId, reply, settings.value.telegramWebAppUrl)
            }

            text.startsWith("/reindex") -> {
                val domainParam = text.removePrefix("/reindex").trim()
                val targetDomain = if (domainParam.isNotEmpty()) domainParam else settings.value.baseDomain
                telegramApi.sendMessage(token, chatId, "⏳ Memproses audit & re-indexing GSC untuk domain: <code>$targetDomain</code>...")
                triggerDomainReindex(targetDomain)
            }

            text.startsWith("/ticket") -> {
                val desc = text.removePrefix("/ticket").trim()
                if (desc.isEmpty()) {
                    telegramApi.sendMessage(token, chatId, "⚠️ Format salah! Gunakan: <code>/ticket [deskripsi kendala]</code>")
                } else {
                    createMemberTicket(
                        memberUsername = username,
                        category = "Kendala Member",
                        priority = MemberTicket.PRIORITY_HIGH,
                        domainAffected = settings.value.baseDomain,
                        issueDescription = desc,
                        adminNotes = "Diterima via bot Telegram",
                        reportedVia = "TELEGRAM_BOT"
                    )
                }
            }

            text.startsWith("/status_ticket") -> {
                val parts = text.removePrefix("/status_ticket").trim().split("\\s+".toRegex())
                if (parts.size < 2) {
                    telegramApi.sendMessage(token, chatId, "⚠️ Format salah! Gunakan: <code>/status_ticket [KODE_TIKET] [OPEN|DIPROSES|SELESAI]</code>\nContoh: <code>/status_ticket TKT-1024 SELESAI</code>")
                } else {
                    val code = parts[0].replace("#", "")
                    val newStatus = when (parts[1].uppercase()) {
                        "SELESAI", "RESOLVED", "DONE" -> MemberTicket.STATUS_RESOLVED
                        "PROSES", "DIPROSES", "IN_PROGRESS" -> MemberTicket.STATUS_IN_PROGRESS
                        else -> MemberTicket.STATUS_OPEN
                    }
                    val ticket = ticketDao.getTicketByCode(code)
                    if (ticket != null) {
                        updateTicketStatus(ticket, newStatus, "Diperbarui oleh @$username via bot")
                        telegramApi.sendMessage(token, chatId, "✅ Status tiket #$code berhasil diubah menjadi <b>$newStatus</b>!")
                    } else {
                        telegramApi.sendMessage(token, chatId, "❌ Tiket dengan kode #$code tidak ditemukan!")
                    }
                }
            }

            text.startsWith("/gsc_inspect") -> {
                val domainParam = text.removePrefix("/gsc_inspect").trim()
                val target = if (domainParam.isNotEmpty()) domainParam else settings.value.baseDomain
                telegramApi.sendMessage(token, chatId, "🔍 Memeriksa status Google Console & Marketing Tags untuk <code>$target</code>...")
                val res = gscService.inspectDomainGsc(target)
                val reply = """
                    🔍 <b>HASIL INSPEKSI GOOGLE SEARCH CONSOLE</b>
                    ━━━━━━━━━━━━━━━━━━━━
                    🌐 <b>Domain:</b> <code>https://${res.domain}</code>
                    📊 <b>Status Index:</b> <b>${res.indexingStatus}</b>
                    🌐 <b>HTTP Response:</b> ${res.httpCode} (${if (res.isReachable) "Online" else "Offline"})
                    🏷️ <b>Tag Verifikasi:</b> ${if (res.hasVerificationTag) "Ada (" + res.verificationTagFound + ")" else "Belum terdeteksi di /marketing-tags"}
                    🗺️ <b>Sitemap:</b> ${if (res.hasSitemap) "Tersedia" else "Tidak ditemukan"}
                """.trimIndent()
                telegramApi.sendMessage(token, chatId, reply)
            }

            text.startsWith("/list_domains") -> {
                val reply = generateGscSummaryReport()
                telegramApi.sendMessage(token, chatId, reply)
            }

            text.startsWith("/report") -> {
                val reply = generateReportText(includeDomains = true, includeTickets = true, onlyOpenTickets = false)
                telegramApi.sendMessage(token, chatId, reply)
            }
        }
    }

    // ==========================================
    // SETTINGS & TOKEN VERIFICATION
    // ==========================================
    fun saveSettings(newSettings: AppSettings) {
        settingsRepo.updateSettings(newSettings)
        logBot("Pengaturan berhasil disimpan.")
    }

    fun verifyBotToken(token: String) {
        viewModelScope.launch {
            _botVerificationResult.value = "Memeriksa token bot..."
            val result = telegramApi.verifyBotToken(token)
            result.onSuccess { msg ->
                _botVerificationResult.value = "✅ $msg"
                logBot("Verifikasi token sukses: $msg")
            }.onFailure { err ->
                _botVerificationResult.value = "❌ Gagal: ${err.message}"
                logBot("Verifikasi token gagal: ${err.message}")
            }
        }
    }

    fun clearBotVerification() {
        _botVerificationResult.value = null
        _botCommandDeployResult.value = null
    }

    // ==========================================
    // REPORT GENERATOR & DISPATCHER
    // ==========================================
    fun generateReportText(
        includeDomains: Boolean = true,
        includeTickets: Boolean = true,
        onlyOpenTickets: Boolean = false
    ): String {
        val s = settings.value
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale.forLanguageTag("id-ID"))
        val nowStr = dateFormat.format(Date())

        val currentTickets = if (onlyOpenTickets) {
            tickets.value.filter { it.status != MemberTicket.STATUS_RESOLVED }
        } else {
            tickets.value
        }
        val currentDomains = domains.value

        val sb = StringBuilder()
        sb.append("📋 <b>LAPORAN OPERASIONAL BACKOFFICE & DISPATCHER</b>\n")
        sb.append("📅 <i>Waktu: $nowStr</i>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("👤 <b>Admin Tujuan:</b> <code>${s.telegramAdminId}</code> (@${s.telegramAdminUsername})\n")
        sb.append("👥 <b>Grup:</b> ${s.telegramGroupLink}\n")
        sb.append("🌐 <b>Panel Base:</b> <code>https://${s.baseDomain}${s.backofficePath}</code>\n")
        sb.append("🏷️ <b>Marketing Tags:</b> <code>https://${s.baseDomain}${s.marketingTagsPath}</code>\n")
        sb.append("🔍 <b>Google Console:</b> ${s.googleSearchConsoleUrl}\n")
        sb.append("📱 <b>Web App:</b> ${s.telegramWebAppUrl}\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

        if (includeTickets) {
            sb.append("🎫 <b>PENDATAAN KENDALA MEMBER (${currentTickets.size}):</b>\n")
            if (currentTickets.isEmpty()) {
                sb.append("<i>Tidak ada tiket kendala aktif saat ini.</i>\n\n")
            } else {
                currentTickets.forEachIndexed { index, t ->
                    val priorityEmoji = when (t.priority) {
                        MemberTicket.PRIORITY_HIGH -> "🔴 [TINGGI]"
                        MemberTicket.PRIORITY_MEDIUM -> "🟡 [SEDANG]"
                        else -> "🟢 [RENDAH]"
                    }
                    val statusEmoji = when (t.status) {
                        MemberTicket.STATUS_OPEN -> "⏳ BARU"
                        MemberTicket.STATUS_IN_PROGRESS -> "⚙️ PROSES"
                        else -> "✅ SELESAI"
                    }
                    sb.append("${index + 1}. <b>#${t.ticketCode}</b> - <b>${t.memberUsername}</b> $priorityEmoji | $statusEmoji\n")
                    sb.append("   • Kategori: ${t.category}\n")
                    if (t.domainAffected.isNotEmpty()) {
                        sb.append("   • Domain: <code>${t.domainAffected}</code>\n")
                    }
                    sb.append("   • Kendala: ${t.issueDescription}\n")
                    if (t.adminNotes.isNotEmpty()) {
                        sb.append("   • Catatan Admin: <i>${t.adminNotes}</i>\n")
                    }
                    sb.append("\n")
                }
            }
        }

        if (includeDomains) {
            sb.append("🌐 <b>DAFTAR REQUEST DOMAIN & GSC (${currentDomains.size}):</b>\n")
            if (currentDomains.isEmpty()) {
                sb.append("<i>Tidak ada request domain.</i>\n\n")
            } else {
                currentDomains.forEachIndexed { index, d ->
                    val statusText = when (d.status) {
                        DomainRequest.STATUS_ACTIVE -> "🟢 AKTIF"
                        DomainRequest.STATUS_INDEXED -> "⭐ GSC INDEXED"
                        DomainRequest.STATUS_BLOCKED -> "🚫 BLOKIR NAWALA"
                        else -> "⏳ PENDING DNS"
                    }
                    sb.append("${index + 1}. <b>${d.domainName}</b> [$statusText]\n")
                    sb.append("   • GSC Status: <b>${d.gscIndexingStatus}</b> | Path: ${d.targetPath}\n")
                    sb.append("   • DNS: ${d.dnsSettings}\n")
                    if (d.notes.isNotEmpty()) {
                        sb.append("   • Catatan: ${d.notes}\n")
                    }
                    sb.append("\n")
                }
            }
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🤖 <i>Dikirim otomatis via Backoffice Bot Suite</i>")

        return sb.toString()
    }

    fun sendTelegramReport(targetChatId: String, reportText: String) {
        val token = settings.value.telegramBotToken
        viewModelScope.launch {
            _telegramStatus.value = TelegramUiStatus.Sending
            val result = telegramApi.sendMessage(
                botToken = token,
                chatId = targetChatId,
                text = reportText,
                parseMode = "HTML"
            )
            when (result) {
                is TelegramSendResult.Success -> {
                    _telegramStatus.value = TelegramUiStatus.Success(
                        "Laporan terkirim sukses ke ID $targetChatId!",
                        result.messageId
                    )
                    logBot("Laporan terkirim ke ID $targetChatId (Msg ID: ${result.messageId})")
                }
                is TelegramSendResult.Error -> {
                    _telegramStatus.value = TelegramUiStatus.Error(result.errorMessage)
                    logBot("Gagal kirim ke ID $targetChatId: ${result.errorMessage}")
                }
            }
        }
    }

    fun resetTelegramStatus() {
        _telegramStatus.value = TelegramUiStatus.Idle
    }

    // ==========================================
    // GEMINI AI ASSISTANT
    // ==========================================
    fun sendChatMessage(text: String) {
        val userText = text.trim()
        if (userText.isEmpty()) return

        val userMsg = ChatMessage(role = "user", content = userText)
        _chatMessages.value = _chatMessages.value + userMsg

        viewModelScope.launch {
            _isChatLoading.value = true
            val effectiveKey = settingsRepo.getEffectiveGeminiApiKey()
            val result = geminiApi.generateChatResponse(
                apiKey = effectiveKey,
                history = _chatMessages.value.dropLast(1),
                userPrompt = userText
            )
            _isChatLoading.value = false
            result.onSuccess { reply ->
                _chatMessages.value = _chatMessages.value + ChatMessage(role = "model", content = reply)
            }.onFailure { err ->
                val errorMsg = err.message ?: "Gagal memproses pesan AI"
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    role = "model",
                    content = "⚠️ $errorMsg",
                    isError = true
                )
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(
            ChatMessage(
                role = "model",
                content = "Riwayat chat telah dibersihkan. Apa yang bisa saya bantu terkait panel backoffice, domain, Google Search Console, atau kendala member?"
            )
        )
    }

    fun askAiToAnalyzeIssues() {
        val openTickets = tickets.value.filter { it.status != MemberTicket.STATUS_RESOLVED }
        val prompt = if (openTickets.isEmpty()) {
            "Tolong berikan checklist audit keamanan dan ketersediaan domain backoffice, panduan mengatasi domain terblokir nawala, dan cara setting marketing tags di /backoffice/settings/marketing-tags."
        } else {
            val listStr = openTickets.joinToString("\n") {
                "- #${it.ticketCode} Member: ${it.memberUsername}, Kategori: ${it.category}, Domain: ${it.domainAffected}, Deskripsi: ${it.issueDescription}, Prioritas: ${it.priority}"
            }
            "Berikut adalah daftar kendala member saat ini:\n$listStr\n\nTolong analisakan akar masalah kendala ini, berikan solusi perbaikan cepat (troubleshooting), rekomendasi DNS/domain alternatif, dan langkah tindak lanjut yang perlu dilakukan admin."
        }
        sendChatMessage(prompt)
    }

    fun askAiForGscGuide() {
        val baseDomain = settings.value.baseDomain
        val prompt = "Bagaimana alur lengkap melakukan index ulang domain '$baseDomain' di Google Search Console (https://search.google.com/search-console/welcome)? Serta bagaimana cara mengonfigurasi marketing tags di '/backoffice/settings/marketing-tags' agar verifikasi kepemilikan dan tracking pixel berjalan sukses?"
        sendChatMessage(prompt)
    }

    // ==========================================
    // CLOUDFLARE WORKERS WEBHOOK & FREE TIER
    // ==========================================
    fun setTelegramWebhook(webhookUrl: String) {
        viewModelScope.launch {
            _webhookActionLoading.value = true
            val token = settings.value.telegramBotToken.trim()
            if (token.isEmpty()) {
                _webhookInfoResult.value = "❌ Token bot Telegram belum diisi! Silakan masukkan token dari @BotFather di Pengaturan."
                _webhookActionLoading.value = false
                return@launch
            }
            val cleanUrl = webhookUrl.trim()
            if (cleanUrl.isEmpty() || !cleanUrl.startsWith("https://")) {
                _webhookInfoResult.value = "❌ URL Webhook harus valid HTTPS!\nContoh: https://backoffice-bot.subdomain.workers.dev/webhook"
                _webhookActionLoading.value = false
                return@launch
            }

            saveSettings(settings.value.copy(cloudflareWorkerUrl = cleanUrl))
            logBot("Mengirim setWebhook ke Telegram: $cleanUrl")

            val result = telegramApi.setWebhook(token, cleanUrl)
            _webhookActionLoading.value = false
            result.onSuccess { msg ->
                _webhookInfoResult.value = "✅ Webhook Berhasil Diset!\n$msg\nURL: $cleanUrl\n(Bot Telegram sekarang berjalan 24/7 di Cloudflare Workers Free Tier)"
                logBot("setWebhook sukses: $msg")
                if (_isPollerRunning.value) {
                    stopBotPoller()
                    logBot("Poller lokal HP dihentikan karena Webhook Cloudflare telah aktif.")
                }
            }.onFailure { err ->
                _webhookInfoResult.value = "❌ Gagal setWebhook:\n${err.message}"
                logBot("setWebhook error: ${err.message}")
            }
        }
    }

    fun getTelegramWebhookInfo() {
        viewModelScope.launch {
            _webhookActionLoading.value = true
            val token = settings.value.telegramBotToken.trim()
            if (token.isEmpty()) {
                _webhookInfoResult.value = "❌ Token bot Telegram belum diisi!"
                _webhookActionLoading.value = false
                return@launch
            }

            val result = telegramApi.getWebhookInfo(token)
            _webhookActionLoading.value = false
            result.onSuccess { info ->
                val url = info.optString("url", "")
                val pendingUpdates = info.optInt("pending_update_count", 0)
                val lastError = info.optString("last_error_message", "")
                val lastErrorDate = info.optLong("last_error_date", 0L)

                val text = buildString {
                    append("ℹ️ Status Webhook Telegram Saat Ini:\n")
                    if (url.isNotEmpty()) {
                        append("• URL Aktif: ").append(url).append("\n")
                        append("• Mode: ☁️ Cloudflare Workers Serverless 24/7\n")
                    } else {
                        append("• URL Aktif: ❌ Tidak Ada (Mode Polling Lokal HP)\n")
                    }
                    append("• Pending Updates: ").append(pendingUpdates).append("\n")
                    if (lastErrorDate > 0) {
                        append("• Error Terakhir: ").append(lastError).append("\n")
                    } else {
                        append("• Status Koneksi: 🟢 Sehat (Tanpa Error)\n")
                    }
                }
                _webhookInfoResult.value = text
                logBot("getWebhookInfo: URL=$url, pending=$pendingUpdates")
            }.onFailure { err ->
                _webhookInfoResult.value = "❌ Gagal mengambil Webhook Info:\n${err.message}"
            }
        }
    }

    fun deleteTelegramWebhook() {
        viewModelScope.launch {
            _webhookActionLoading.value = true
            val token = settings.value.telegramBotToken.trim()
            if (token.isEmpty()) {
                _webhookInfoResult.value = "❌ Token bot Telegram belum diisi!"
                _webhookActionLoading.value = false
                return@launch
            }

            val result = telegramApi.deleteWebhook(token)
            _webhookActionLoading.value = false
            result.onSuccess { msg ->
                _webhookInfoResult.value = "✅ Webhook Berhasil Dihapus!\n$msg\nSekarang Anda dapat mengaktifkan Poller Bot lokal di aplikasi ini jika diperlukan."
                logBot("deleteWebhook sukses: $msg")
            }.onFailure { err ->
                _webhookInfoResult.value = "❌ Gagal menghapus Webhook:\n${err.message}"
            }
        }
    }

    fun clearWebhookResult() {
        _webhookInfoResult.value = null
    }
}
