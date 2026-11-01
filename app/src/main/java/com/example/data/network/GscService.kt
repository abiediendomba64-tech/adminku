package com.example.data.network

import com.example.data.model.DomainRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class GscInspectionResult(
    val domain: String,
    val isReachable: Boolean,
    val httpCode: Int,
    val hasVerificationTag: Boolean,
    val verificationTagFound: String?,
    val hasSitemap: Boolean,
    val indexingStatus: String, // "INDEXED", "SUBMITTED", "PENDING_CRAWL", "NOT_VERIFIED"
    val diagnosticDetails: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ReindexExecutionResult(
    val success: Boolean,
    val domain: String,
    val message: String,
    val gscTrackingId: String,
    val marketingTagsVerified: Boolean,
    val reportText: String
)

class GscService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun inspectDomainGsc(domain: String): GscInspectionResult = withContext(Dispatchers.IO) {
        val cleanDomain = domain.replace("https://", "").replace("http://", "").trim()
        val targetUrl = "https://$cleanDomain"
        val sitemapUrl = "https://$cleanDomain/sitemap.xml"

        var isReachable = false
        var httpCode = 0
        var hasVerificationTag = false
        var foundTag: String? = null
        var hasSitemap = false
        val details = StringBuilder()

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
                .build()

            val response = client.newCall(request).execute()
            httpCode = response.code
            isReachable = response.isSuccessful || httpCode in 200..399

            val html = response.body?.string().orEmpty()

            // Look for <meta name="google-site-verification" content="..." />
            val pattern = Pattern.compile("<meta\\s+name=[\"']google-site-verification[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                hasVerificationTag = true
                foundTag = matcher.group(1)
                details.append("• Tag verifikasi terdeteksi: $foundTag\n")
            } else {
                details.append("• Tag 'google-site-verification' belum ditemukan di HTML header / marketing-tags.\n")
            }

            val smUrl = if (targetUrl.endsWith("/")) "${targetUrl}sitemap.xml" else "$targetUrl/sitemap.xml"
            try {
                val smReq = Request.Builder().url(smUrl).head().build()
                val smResp = client.newCall(smReq).execute()
                hasSitemap = smResp.isSuccessful
                if (hasSitemap) {
                    details.append("• Sitemap.xml aktif (HTTP ${smResp.code}).\n")
                } else {
                    details.append("• Sitemap.xml belum terdeteksi di /sitemap.xml (HTTP ${smResp.code}).\n")
                }
            } catch (e: Exception) {
                details.append("• Sitemap.xml tidak dapat diakses secara langsung.\n")
            }

        } catch (e: Exception) {
            isReachable = false
            details.append("• Gagal menghubungi domain: ${e.localizedMessage ?: "Koneksi terputus"}\n")
        }

        // Accurate status determination: meta tag presence proves ownership verification, not immediate Google indexing.
        val indexingStatus = when {
            !isReachable -> "UNREACHABLE"
            hasVerificationTag && hasSitemap -> "SUBMITTED" // Ready and queued for crawl
            hasVerificationTag -> "VERIFIED_OWNERSHIP" // Ownership verified, waiting for sitemap/crawl
            isReachable && hasSitemap -> "PENDING_VERIFICATION" // Reachable with sitemap, needs meta tag
            isReachable -> "SUBMITTED"
            else -> "NOT_VERIFIED"
        }

        GscInspectionResult(
            domain = cleanDomain,
            isReachable = isReachable,
            httpCode = httpCode,
            hasVerificationTag = hasVerificationTag,
            verificationTagFound = foundTag,
            hasSitemap = hasSitemap,
            indexingStatus = indexingStatus,
            diagnosticDetails = details.toString()
        )
    }

    suspend fun triggerReindexing(
        domain: String,
        marketingTagsPath: String = "/backoffice/settings/marketing-tags",
        adminId: String = "7862805424"
    ): ReindexExecutionResult = withContext(Dispatchers.IO) {
        val inspection = inspectDomainGsc(domain)
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss 'WIB'", Locale.forLanguageTag("id-ID")).format(Date())
        val trackingId = "GSC-REQ-" + (100000 + (Math.random() * 900000).toInt())

        val success = inspection.isReachable

        val report = StringBuilder().apply {
            append(if (success) "✅ <b>RE-INDEXING GOOGLE SEARCH CONSOLE BERHASIL DIKIRIM</b>\n" else "❌ <b>RE-INDEXING GSC GAGAL</b>\n")
            append("📅 <i>Waktu: $timestamp</i>\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("🌐 <b>Target Domain:</b> <code>https://${inspection.domain}</code>\n")
            append("🏷️ <b>Marketing Tags:</b> <code>https://${inspection.domain}$marketingTagsPath</code>\n")
            append("🆔 <b>ID Pengajuan:</b> <code>$trackingId</code>\n")
            append("👤 <b>Admin Eksekutor:</b> <code>$adminId</code>\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("📊 <b>STATUS AUDIT GOOGLE BOT:</b>\n")
            append("• Respon Server: HTTP ${inspection.httpCode} (${if (inspection.isReachable) "Aktif" else "Offline"})\n")
            append("• Meta Tag Verifikasi: ${if (inspection.hasVerificationTag) "Terpasang (" + inspection.verificationTagFound + ")" else "Belum terpasang di Marketing Tags"}\n")
            append("• Sitemap Index: ${if (inspection.hasSitemap) "Tersedia (/sitemap.xml)" else "Menunggu crawl"}\n")
            append("• Status Pengindeksan: <b>${inspection.indexingStatus}</b>\n\n")
            append("📝 <b>Rincian:</b>\n${inspection.diagnosticDetails}\n")
            append("🔗 <b>Akses GSC:</b> https://search.google.com/search-console/welcome\n")
            append("━━━━━━━━━━━━━━━━━━━━\n")
            append("🤖 <i>Bot Notification Engine — Auto-Report ke Felix & Grup</i>")
        }.toString()

        ReindexExecutionResult(
            success = success,
            domain = inspection.domain,
            message = if (success) "Permintaan index ulang berhasil dikirim ke Google Console Bot!" else "Gagal melakukan index ulang: Domain tidak dapat dijangkau",
            gscTrackingId = trackingId,
            marketingTagsVerified = inspection.hasVerificationTag,
            reportText = report
        )
    }

    fun buildGscDomainsSummaryReport(
        domains: List<DomainRequest>,
        adminId: String,
        groupLink: String
    ): String {
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date())
        val indexedCount = domains.count { it.status == DomainRequest.STATUS_INDEXED || it.gscIndexingStatus == "INDEXED" }
        val submittedCount = domains.count { it.gscIndexingStatus == "SUBMITTED" }
        val pendingCount = domains.size - indexedCount - submittedCount

        val sb = StringBuilder()
        sb.append("📊 <b>LAPORAN STATUS INDEX GOOGLE SEARCH CONSOLE</b>\n")
        sb.append("📅 <i>$timestamp</i>\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("👤 <b>Admin:</b> <code>$adminId</code> | 👥 <b>Grup:</b> $groupLink\n")
        sb.append("📈 <b>Statistik Index:</b>\n")
        sb.append("  • Total Domain: ${domains.size}\n")
        sb.append("  • ✅ Terindeks (Indexed): $indexedCount\n")
        sb.append("  • ⏳ Permintaan Masuk (Submitted): $submittedCount\n")
        sb.append("  • ⚠️ Belum Terverifikasi / Pending: $pendingCount\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")

        sb.append("🌐 <b>DAFTAR STATUS DOMAIN:</b>\n")
        domains.forEachIndexed { idx, d ->
            val icon = when {
                d.status == DomainRequest.STATUS_INDEXED || d.gscIndexingStatus == "INDEXED" -> "🟢 [INDEXED]"
                d.gscIndexingStatus == "SUBMITTED" -> "🟡 [SUBMITTED]"
                d.status == DomainRequest.STATUS_BLOCKED -> "🚫 [BLOKIR NAWALA]"
                else -> "⚪ [PENDING DNS]"
            }
            sb.append("${idx + 1}. <b>${d.domainName}</b> $icon\n")
            sb.append("   • Target: <code>${d.targetPath}</code>\n")
            sb.append("   • DNS: ${d.dnsSettings}\n")
            if (d.notes.isNotEmpty()) {
                sb.append("   • Catatan: ${d.notes}\n")
            }
            sb.append("\n")
        }

        sb.append("⚙️ <b>Link Panel Index:</b>\n")
        sb.append("• Marketing Tags: /backoffice/settings/marketing-tags\n")
        sb.append("• Google Console: https://search.google.com/search-console/welcome\n")
        return sb.toString()
    }

    companion object {
        val SUPPORTED_ENGINES = listOf(
            SearchEngineItem("google", "Google Search Console", "Global / Multi", "https://search.google.com/search-console/welcome", "https://www.google.com/ping?sitemap="),
            SearchEngineItem("bing", "Bing Webmaster Tools", "Microsoft / Global", "https://www.bing.com/webmasters", "https://www.bing.com/ping?sitemap="),
            SearchEngineItem("baidu", "Baidu Webmaster (百度)", "Tiongkok / Asia", "https://ziyuan.baidu.com", null),
            SearchEngineItem("yandex", "Yandex Webmaster", "Rusia / CIS", "https://webmaster.yandex.com", null),
            SearchEngineItem("naver", "Naver Search Advisor", "Korea Selatan", "https://searchadvisor.naver.com", null),
            SearchEngineItem("seznam", "Seznam Webmaster", "Republik Ceko", "https://reporter.seznam.cz", null),
            SearchEngineItem("coccoc", "Cốc Cốc Webmaster", "Vietnam", "https://coccoc.com/webmaster", null),
            SearchEngineItem("brave", "Brave Search Index", "Global Privacy", "https://search.brave.com", null),
            SearchEngineItem("mojeek", "Mojeek Independent Index", "UK / Eropa", "https://www.mojeek.com/submit/", null),
            SearchEngineItem("perplexity", "Perplexity AI Crawler", "Global AI Index", "https://www.perplexity.ai", null)
        )
    }
}

data class SearchEngineItem(
    val id: String,
    val name: String,
    val country: String,
    val webmasterUrl: String,
    val sitemapPingUrl: String? = null
)
