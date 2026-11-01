package com.example.data.network

import android.util.Log
import com.example.data.model.MemberTicket
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "TelegramMessageService"

// ==========================================
// MOSHI DATA MODELS FOR TELEGRAM BOT API
// ==========================================

@JsonClass(generateAdapter = true)
data class TelegramSendMessagePayload(
    @Json(name = "chat_id") val chatId: String,
    @Json(name = "text") val text: String,
    @Json(name = "parse_mode") val parseMode: String = "HTML",
    @Json(name = "disable_web_page_preview") val disableWebPagePreview: Boolean = false,
    @Json(name = "reply_markup") val replyMarkup: TelegramInlineKeyboard? = null
)

@JsonClass(generateAdapter = true)
data class TelegramInlineKeyboard(
    @Json(name = "inline_keyboard") val inlineKeyboard: List<List<TelegramInlineButton>>
)

@JsonClass(generateAdapter = true)
data class TelegramInlineButton(
    @Json(name = "text") val text: String,
    @Json(name = "url") val url: String? = null,
    @Json(name = "web_app") val webApp: TelegramWebApp? = null
)

@JsonClass(generateAdapter = true)
data class TelegramWebApp(
    @Json(name = "url") val url: String
)

@JsonClass(generateAdapter = true)
data class TelegramSetWebhookPayload(
    @Json(name = "url") val url: String,
    @Json(name = "drop_pending_updates") val dropPendingUpdates: Boolean = true
)

@JsonClass(generateAdapter = true)
data class TelegramDeleteWebhookPayload(
    @Json(name = "drop_pending_updates") val dropPendingUpdates: Boolean = true
)

@JsonClass(generateAdapter = true)
data class TelegramApiResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: T? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessageData(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "date") val date: Long? = null,
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramWebhookInfoData(
    @Json(name = "url") val url: String? = "",
    @Json(name = "has_custom_certificate") val hasCustomCertificate: Boolean? = false,
    @Json(name = "pending_update_count") val pendingUpdateCount: Int? = 0,
    @Json(name = "last_error_date") val lastErrorDate: Long? = null,
    @Json(name = "last_error_message") val lastErrorMessage: String? = null
)

data class TelegramDispatchSummary(
    val success: Boolean,
    val adminSent: Boolean,
    val groupSent: Boolean,
    val summaryText: String,
    val messageId: Long? = null
)

// ==========================================
// RETROFIT API INTERFACE
// ==========================================

interface TelegramRetrofitApi {
    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Body payload: TelegramSendMessagePayload
    ): Response<TelegramApiResponse<TelegramMessageData>>

    @POST("bot{token}/setWebhook")
    suspend fun setWebhook(
        @Path("token") token: String,
        @Body payload: TelegramSetWebhookPayload
    ): Response<TelegramApiResponse<Boolean>>

    @GET("bot{token}/getWebhookInfo")
    suspend fun getWebhookInfo(
        @Path("token") token: String
    ): Response<TelegramApiResponse<TelegramWebhookInfoData>>

    @POST("bot{token}/deleteWebhook")
    suspend fun deleteWebhook(
        @Path("token") token: String,
        @Body payload: TelegramDeleteWebhookPayload
    ): Response<TelegramApiResponse<Boolean>>
}

// ==========================================
// TELEGRAM MESSAGE SERVICE IMPLEMENTATION
// ==========================================

class TelegramMessageService(
    private val api: TelegramRetrofitApi = defaultApi
) {
    companion object {
        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        val defaultApi: TelegramRetrofitApi by lazy {
            Retrofit.Builder()
                .baseUrl("https://api.telegram.org/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TelegramRetrofitApi::class.java)
        }
    }

    /**
     * Dispatch new Member Ticket notification to Admin and Group via Retrofit
     */
    suspend fun dispatchTicketCreated(
        botToken: String,
        ticket: MemberTicket,
        adminId: String,
        groupId: String,
        webAppUrl: String
    ): Result<TelegramDispatchSummary> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Token bot Telegram belum diisi!"))
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date(ticket.createdAt))
        val priorityEmoji = when (ticket.priority) {
            MemberTicket.PRIORITY_HIGH -> "🔴"
            MemberTicket.PRIORITY_MEDIUM -> "🟡"
            else -> "🟢"
        }

        val text = """
            🚨 <b>[TIKET KELUHAN MEMBER BARU]</b>
            ━━━━━━━━━━━━━━━━━━━━
            • <b>Kode Tiket:</b> <code>#${ticket.ticketCode}</code>
            • <b>Pelapor:</b> <b>${escapeHtml(ticket.memberUsername)}</b>
            • <b>Kategori:</b> ${escapeHtml(ticket.category)}
            • <b>Prioritas:</b> $priorityEmoji <b>${ticket.priority}</b>
            • <b>Domain Terdampak:</b> <code>${escapeHtml(ticket.domainAffected)}</code>
            • <b>Saluran Laporan:</b> <code>${ticket.reportedVia}</code>
            • <b>Waktu:</b> $dateStr
            ━━━━━━━━━━━━━━━━━━━━
            📝 <b>Deskripsi Kendala:</b>
            <i>${escapeHtml(ticket.issueDescription)}</i>
            ${if (ticket.adminNotes.isNotBlank()) "\n💬 <b>Catatan Awal:</b> <i>" + escapeHtml(ticket.adminNotes) + "</i>" else ""}
            ━━━━━━━━━━━━━━━━━━━━
            🤖 <i>Dispatched via TelegramMessageService (Retrofit)</i>
        """.trimIndent()

        val buttons = mutableListOf<List<TelegramInlineButton>>()
        if (webAppUrl.isNotBlank()) {
            buttons.add(listOf(TelegramInlineButton("🌐 Buka di Web App Panel", webApp = TelegramWebApp(webAppUrl))))
        }
        val cleanUser = ticket.memberUsername.removePrefix("@")
        if (cleanUser.isNotBlank() && !cleanUser.contains(" ")) {
            buttons.add(listOf(TelegramInlineButton("💬 Hubungi Pelapor (@$cleanUser)", url = "https://t.me/$cleanUser")))
        }

        val keyboard = if (buttons.isNotEmpty()) TelegramInlineKeyboard(buttons) else null

        var adminOk = false
        var groupOk = false
        var lastMsgId: Long? = null

        // 1. Dispatch to Admin ID
        if (adminId.isNotBlank()) {
            try {
                val resp = api.sendMessage(trimmedToken, TelegramSendMessagePayload(adminId, text, replyMarkup = keyboard))
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    adminOk = true
                    lastMsgId = resp.body()?.result?.messageId
                } else {
                    Log.e(TAG, "Gagal kirim ke Admin: ${resp.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception kirim ke Admin", e)
            }
        }

        // 2. Dispatch to Telegram Group
        if (groupId.isNotBlank()) {
            try {
                val resp = api.sendMessage(trimmedToken, TelegramSendMessagePayload(groupId, text, replyMarkup = keyboard))
                if (resp.isSuccessful && resp.body()?.ok == true) {
                    groupOk = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception kirim ke Group", e)
            }
        }

        if (adminOk || groupOk) {
            Result.success(
                TelegramDispatchSummary(
                    success = true,
                    adminSent = adminOk,
                    groupSent = groupOk,
                    summaryText = "Tiket #${ticket.ticketCode} berhasil dikirim ke Admin: $adminOk, Group: $groupOk",
                    messageId = lastMsgId
                )
            )
        } else {
            Result.failure(Exception("Gagal mendispatch tiket ke Telegram (Admin: $adminOk, Group: $groupOk)"))
        }
    }

    /**
     * Dispatch Member Ticket status update notification
     */
    suspend fun dispatchTicketStatusUpdated(
        botToken: String,
        ticket: MemberTicket,
        oldStatus: String,
        newStatus: String,
        adminId: String,
        groupId: String
    ): Result<TelegramDispatchSummary> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Token bot Telegram kosong"))
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val statusEmoji = when (newStatus) {
            MemberTicket.STATUS_RESOLVED -> "✅"
            MemberTicket.STATUS_IN_PROGRESS -> "⏳"
            else -> "🟡"
        }

        val text = """
            🔄 <b>[UPDATE STATUS TIKET #${ticket.ticketCode}]</b>
            ━━━━━━━━━━━━━━━━━━━━
            • <b>Status Lama:</b> <s>$oldStatus</s>
            • <b>Status Baru:</b> $statusEmoji <b>$newStatus</b>
            • <b>Member:</b> <b>${escapeHtml(ticket.memberUsername)}</b>
            • <b>Domain:</b> <code>${escapeHtml(ticket.domainAffected)}</code>
            • <b>Waktu Pembaruan:</b> $dateStr
            ━━━━━━━━━━━━━━━━━━━━
            📝 <b>Kendala:</b>
            <i>${escapeHtml(ticket.issueDescription)}</i>
            ${if (ticket.adminNotes.isNotBlank()) "\n💬 <b>Catatan Admin:</b>\n<i>" + escapeHtml(ticket.adminNotes) + "</i>" else ""}
            ━━━━━━━━━━━━━━━━━━━━
            🤖 <i>Dispatched via Retrofit TelegramMessageService</i>
        """.trimIndent()

        var adminOk = false
        var groupOk = false

        if (adminId.isNotBlank()) {
            try {
                val resp = api.sendMessage(trimmedToken, TelegramSendMessagePayload(adminId, text))
                adminOk = resp.isSuccessful && resp.body()?.ok == true
            } catch (e: Exception) {
                Log.e(TAG, "Gagal dispatch status ke admin", e)
            }
        }

        if (groupId.isNotBlank()) {
            try {
                val resp = api.sendMessage(trimmedToken, TelegramSendMessagePayload(groupId, text))
                groupOk = resp.isSuccessful && resp.body()?.ok == true
            } catch (e: Exception) {
                Log.e(TAG, "Gagal dispatch status ke grup", e)
            }
        }

        Result.success(
            TelegramDispatchSummary(
                success = adminOk || groupOk,
                adminSent = adminOk,
                groupSent = groupOk,
                summaryText = "Status tiket #${ticket.ticketCode} ($newStatus) disiarkan ke Telegram."
            )
        )
    }

    /**
     * Dispatch AI-Generated Response Suggestion to Admin/Group
     */
    suspend fun dispatchAiSuggestedReply(
        botToken: String,
        ticket: MemberTicket,
        suggestion: String,
        adminId: String,
        groupId: String
    ): Result<TelegramDispatchSummary> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) return@withContext Result.failure(IllegalStateException("Token bot belum diatur"))

        val text = """
            ✨ <b>[REKOMENDASI SOLUSI AI UNTUK #${ticket.ticketCode}]</b>
            ━━━━━━━━━━━━━━━━━━━━
            • <b>Member:</b> ${escapeHtml(ticket.memberUsername)}
            • <b>Kendala:</b> ${escapeHtml(ticket.issueDescription)}
            ━━━━━━━━━━━━━━━━━━━━
            💡 <b>Solusi / Draft Balasan Cerdas (Gemini AI):</b>
            ${escapeHtml(suggestion)}
            ━━━━━━━━━━━━━━━━━━━━
            <i>Disusun oleh Gemini AI Assistant terintegrasi Dashboard Backoffice.</i>
        """.trimIndent()

        var adminOk = false
        if (adminId.isNotBlank()) {
            try {
                val resp = api.sendMessage(trimmedToken, TelegramSendMessagePayload(adminId, text))
                adminOk = resp.isSuccessful && resp.body()?.ok == true
            } catch (e: Exception) {
                Log.e(TAG, "Error sending AI suggestion", e)
            }
        }

        Result.success(TelegramDispatchSummary(adminOk, adminOk, false, "Draft saran AI dikirim ke Admin."))
    }

    /**
     * General Send Message via Retrofit
     */
    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        replyMarkup: TelegramInlineKeyboard? = null
    ): Result<TelegramMessageData> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        val trimmedChatId = chatId.trim()
        if (trimmedToken.isEmpty() || trimmedChatId.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Token bot atau Chat ID kosong"))
        }

        try {
            val payload = TelegramSendMessagePayload(
                chatId = trimmedChatId,
                text = text,
                parseMode = "HTML",
                replyMarkup = replyMarkup
            )
            val response = api.sendMessage(trimmedToken, payload)
            if (response.isSuccessful && response.body()?.ok == true) {
                val data = response.body()?.result ?: TelegramMessageData(0L)
                Result.success(data)
            } else {
                val err = response.body()?.description ?: "HTTP ${response.code()}: ${response.message()}"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
