package com.example.data.repository

import android.util.Log
import com.example.data.model.MemberTicket
import com.example.data.network.ChatMessage
import com.example.data.network.GeminiApiService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiRepository"

/**
 * Repository using Firebase AI SDK for text-based queries,
 * ticket response suggestions, and GSC intelligence.
 */
class GeminiRepository(
    private val directFallbackApi: GeminiApiService = GeminiApiService()
) {
    // Lazily evaluate Firebase AI generative model as mandated by the skill
    private val firebaseAiModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    /**
     * Handle text-based query for backoffice dashboard assistant
     */
    suspend fun generateTextQuery(
        prompt: String,
        apiKey: String = "",
        history: List<ChatMessage> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. Primary path: Firebase AI SDK with 45s timeout
        try {
            val response = withTimeout(45_000L) {
                firebaseAiModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                return@withContext Result.success(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase AI inference fallback: ${e.message}")
        }

        // 2. Direct API fallback if custom key or BuildConfig key is available
        val effectiveKey = apiKey.trim()
        if (effectiveKey.isNotEmpty()) {
            val fallbackResult = directFallbackApi.generateChatResponse(
                apiKey = effectiveKey,
                history = history,
                userPrompt = prompt,
                modelName = "gemini-2.5-flash"
            )
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }
        }

        Result.failure(Exception("Gagal menghasilkan respons AI. Pastikan koneksi internet aktif atau kunci API terkonfigurasi di Pengaturan."))
    }

    /**
     * Generate response suggestion for a selected member ticket
     */
    suspend fun generateResponseSuggestion(
        ticket: MemberTicket,
        apiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
            Anda adalah Senior Technical Support & Web Operations Specialist.
            Tolong analisa tiket kendala member berikut dan berikan rekomendasi solusi:

            • Kode Tiket: #${ticket.ticketCode}
            • Nama Member: ${ticket.memberUsername}
            • Kategori: ${ticket.category}
            • Prioritas: ${ticket.priority}
            • Domain Terdampak: ${ticket.domainAffected}
            • Deskripsi Kendala: ${ticket.issueDescription}
            ${if (ticket.adminNotes.isNotBlank()) "• Catatan Admin: " + ticket.adminNotes else ""}

            Mohon berikan output berstruktur:
            1. 🔍 Analisis Penyebab Utama (Misal: DNS cache, blokir ISP/Nawala, salah URL redirect, atau sertifikat SSL).
            2. 🛠 Solusi Teknis untuk Admin (Langkah perbaikan di /backoffice atau DNS Cloudflare).
            3. 💬 Draf Template Pesan Balasan untuk Member (Bahasa ramah, sopan, dan jelas untuk dikirim ke chat Telegram).
        """.trimIndent()

        generateTextQuery(prompt, apiKey)
    }

    /**
     * Generate GSC indexing advice for a domain
     */
    suspend fun generateGscAuditAdvice(
        domain: String,
        httpStatus: String,
        metaVerified: Boolean,
        sitemapFound: Boolean,
        apiKey: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
            Tolong berikan panduan audit SEO & Google Search Console untuk domain: $domain
            - Status HTTP: $httpStatus
            - Meta Tag Verifikasi (google-site-verification): ${if (metaVerified) "TERPASANG" else "BELUM TERPASANG"}
            - Sitemap.xml: ${if (sitemapFound) "TERDETEKSI" else "TIDAK DITEMUKAN"}

            Jelaskan langkah verifikasi tag di /backoffice/settings/marketing-tags dan cara mempercepat proses permohonan crawling Googlebot di Search Console.
        """.trimIndent()

        generateTextQuery(prompt, apiKey)
    }
}
