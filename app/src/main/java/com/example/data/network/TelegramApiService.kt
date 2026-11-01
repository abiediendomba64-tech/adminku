package com.example.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.MemberTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

sealed interface TelegramSendResult {
    data class Success(val messageId: Long, val rawResponse: String) : TelegramSendResult
    data class Error(val errorMessage: String, val statusCode: Int? = null) : TelegramSendResult
}

data class TelegramIncomingUpdate(
    val updateId: Long,
    val messageId: Long,
    val chatId: String,
    val fromUsername: String,
    val text: String,
    val isGroup: Boolean,
    val timestamp: Long
)

class TelegramApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        parseMode: String = "HTML",
        replyMarkup: JSONObject? = null
    ): TelegramSendResult = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        val trimmedChatId = chatId.trim()

        if (trimmedToken.isEmpty()) {
            return@withContext TelegramSendResult.Error(
                "Bot Token belum diisi! Silakan masukkan token bot Telegram dari @BotFather di tab Pengaturan atau gunakan opsi kirim via aplikasi Telegram.",
                400
            )
        }
        if (trimmedChatId.isEmpty()) {
            return@withContext TelegramSendResult.Error(
                "Target Chat ID kosong! Masukkan ID Telegram (misal: 7862805424).",
                400
            )
        }

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/sendMessage"
            val jsonPayload = JSONObject().apply {
                put("chat_id", trimmedChatId)
                put("text", text)
                put("parse_mode", parseMode)
                put("disable_web_page_preview", false)
                if (replyMarkup != null) {
                    put("reply_markup", replyMarkup)
                }
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val code = response.code

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                val ok = json.optBoolean("ok", false)
                if (ok) {
                    val resultObj = json.optJSONObject("result")
                    val messageId = resultObj?.optLong("message_id") ?: 0L
                    TelegramSendResult.Success(messageId, responseBody)
                } else {
                    val desc = json.optString("description", "Telegram API mengembalikan kegagalan")
                    TelegramSendResult.Error(desc, code)
                }
            } else {
                val errorDesc = try {
                    val json = JSONObject(responseBody)
                    json.optString("description", "HTTP $code: ${response.message}")
                } catch (e: Exception) {
                    "HTTP $code: ${response.message}"
                }
                TelegramSendResult.Error(errorDesc, code)
            }
        } catch (e: Exception) {
            TelegramSendResult.Error("Gagal menghubungi server Telegram: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    suspend fun verifyBotToken(botToken: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Token bot kosong"))
        }
        try {
            val url = "https://api.telegram.org/bot$trimmedToken/getMe"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                if (json.optBoolean("ok", false)) {
                    val resultObj = json.optJSONObject("result")
                    val botUsername = resultObj?.optString("username", "Unknown") ?: "Unknown"
                    val botFirstName = resultObj?.optString("first_name", "Bot") ?: "Bot"
                    Result.success("Bot Terverifikasi: $botFirstName (@$botUsername)")
                } else {
                    Result.failure(Exception(json.optString("description", "Token bot tidak valid")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: Token bot tidak valid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Register official commands with Telegram Bot API
    suspend fun registerBotCommands(botToken: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Token bot belum diatur"))
        }

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/setMyCommands"
            val commandsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("command", "start")
                    put("description", "Menu Utama & Buka Web App Backoffice")
                })
                put(JSONObject().apply {
                    put("command", "panel")
                    put("description", "Akses /backoffice & /marketing-tags")
                })
                put(JSONObject().apply {
                    put("command", "reindex")
                    put("description", "Trigger Index Ulang GSC (/reindex <domain>)")
                })
                put(JSONObject().apply {
                    put("command", "ticket")
                    put("description", "Buat Tiket Kendala (/ticket <user> <kendala>)")
                })
                put(JSONObject().apply {
                    put("command", "status_ticket")
                    put("description", "Update Status Tiket (/status_ticket <id> <status>)")
                })
                put(JSONObject().apply {
                    put("command", "gsc_inspect")
                    put("description", "Inspeksi Status Index Domain GSC")
                })
                put(JSONObject().apply {
                    put("command", "list_domains")
                    put("description", "Lihat Semua Request Domain & Status Index")
                })
                put(JSONObject().apply {
                    put("command", "report")
                    put("description", "Kirim Laporan Lengkap Terstruktur")
                })
            }

            val payload = JSONObject().apply {
                put("commands", commandsArray)
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful && respBody.isNotEmpty()) {
                val json = JSONObject(respBody)
                if (json.optBoolean("ok", false)) {
                    Result.success("8 Perintah Bot Telegram berhasil didaftarkan ke server Telegram!")
                } else {
                    Result.failure(Exception(json.optString("description", "Gagal mendaftarkan commands")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: Gagal mendaftarkan commands"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Set Telegram Menu Button to open Web App directly in Telegram
    suspend fun setChatMenuButton(botToken: String, webAppUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token bot belum diatur"))

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/setChatMenuButton"
            val payload = JSONObject().apply {
                put("menu_button", JSONObject().apply {
                    put("type", "web_app")
                    put("text", "🌐 Panel Web App")
                    put("web_app", JSONObject().apply {
                        put("url", webAppUrl)
                    })
                })
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful && respBody.isNotEmpty()) {
                val json = JSONObject(respBody)
                if (json.optBoolean("ok", false)) {
                    Result.success("Tombol Web App menu berhasil dikonfigurasi!")
                } else {
                    Result.failure(Exception(json.optString("description", "Gagal mengatur Menu Button")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: Gagal mengatur Menu Button"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Send Message with Inline Web App Button
    suspend fun sendWebAppInvitation(
        botToken: String,
        chatId: String,
        messageText: String,
        webAppUrl: String,
        buttonText: String = "🚀 Buka Web App Backoffice"
    ): TelegramSendResult {
        val inlineKeyboard = JSONObject().apply {
            put("inline_keyboard", JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", buttonText)
                        put("web_app", JSONObject().apply {
                            put("url", webAppUrl)
                        })
                    })
                })
            })
        }
        return sendMessage(botToken, chatId, messageText, "HTML", inlineKeyboard)
    }

    // Poll updates from Telegram (getUpdates)
    suspend fun getUpdates(
        botToken: String,
        offset: Long = 0,
        limit: Int = 10
    ): Result<List<TelegramIncomingUpdate>> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token bot kosong"))

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/getUpdates?offset=$offset&limit=$limit&timeout=0"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful && responseBody.isNotEmpty()) {
                val json = JSONObject(responseBody)
                if (json.optBoolean("ok", false)) {
                    val resultArr = json.optJSONArray("result") ?: JSONArray()
                    val updates = mutableListOf<TelegramIncomingUpdate>()
                    for (i in 0 until resultArr.length()) {
                        val upd = resultArr.getJSONObject(i)
                        val updateId = upd.optLong("update_id")
                        val msg = upd.optJSONObject("message")
                        if (msg != null) {
                            val msgId = msg.optLong("message_id")
                            val chat = msg.optJSONObject("chat")
                            val chatId = chat?.optLong("id")?.toString() ?: ""
                            val chatType = chat?.optString("type", "private") ?: "private"
                            val isGroup = chatType.contains("group")
                            val from = msg.optJSONObject("from")
                            val username = from?.optString("username", from.optString("first_name", "User")) ?: "User"
                            val text = msg.optString("text", "")
                            val date = msg.optLong("date", System.currentTimeMillis() / 1000) * 1000

                            if (text.isNotEmpty()) {
                                updates.add(
                                    TelegramIncomingUpdate(
                                        updateId = updateId,
                                        messageId = msgId,
                                        chatId = chatId,
                                        fromUsername = username,
                                        text = text,
                                        isGroup = isGroup,
                                        timestamp = date
                                    )
                                )
                            }
                        }
                    }
                    Result.success(updates)
                } else {
                    Result.failure(Exception(json.optString("description", "Gagal mengambil updates")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper notification when a member ticket is created
    suspend fun notifyNewTicketCreated(
        botToken: String,
        adminId: String,
        groupId: String,
        ticket: MemberTicket
    ): TelegramSendResult {
        val priorityEmoji = when (ticket.priority) {
            MemberTicket.PRIORITY_HIGH -> "🔴 [TINGGI / DARURAT]"
            MemberTicket.PRIORITY_MEDIUM -> "🟡 [SEDANG]"
            else -> "🟢 [RENDAH]"
        }

        val text = """
            🚨 <b>TIKET KENDALA MEMBER BARU DITERIMA</b>
            ━━━━━━━━━━━━━━━━━━━━
            🆔 <b>Kode Tiket:</b> <code>#${ticket.ticketCode}</code>
            👤 <b>Member:</b> <code>${ticket.memberUsername}</code>
            🏷️ <b>Kategori:</b> ${ticket.category}
            ⚡ <b>Prioritas:</b> $priorityEmoji
            🌐 <b>Domain:</b> ${if (ticket.domainAffected.isNotEmpty()) "<code>" + ticket.domainAffected + "</code>" else "-"}
            📝 <b>Deskripsi Kendala:</b>
            <i>${ticket.issueDescription}</i>
            ━━━━━━━━━━━━━━━━━━━━
            📌 <b>Status:</b> ⏳ BARU (OPEN)
            🤖 <i>Sumber: ${ticket.reportedVia}</i>
            💬 <i>Balas: /status_ticket ${ticket.ticketCode} DIPROSES atau SELESAI</i>
        """.trimIndent()

        // Send to Admin
        val adminResult = sendMessage(botToken, adminId, text)
        // Also send to Group if set
        if (groupId.isNotBlank()) {
            sendMessage(botToken, groupId, text)
        }
        return adminResult
    }

    // Helper notification when a member ticket status is updated
    suspend fun notifyTicketStatusChanged(
        botToken: String,
        adminId: String,
        groupId: String,
        ticket: MemberTicket,
        oldStatus: String,
        newStatus: String
    ): TelegramSendResult {
        val statusEmoji = when (newStatus) {
            MemberTicket.STATUS_OPEN -> "⏳ BARU"
            MemberTicket.STATUS_IN_PROGRESS -> "⚙️ SEDANG DIPROSES"
            else -> "✅ SELESAI / RESOLVED"
        }

        val text = """
            🔄 <b>UPDATE STATUS TIKET KENDALA</b>
            ━━━━━━━━━━━━━━━━━━━━
            🆔 <b>Kode Tiket:</b> <code>#${ticket.ticketCode}</code>
            👤 <b>Member:</b> <code>${ticket.memberUsername}</code>
            🏷️ <b>Kategori:</b> ${ticket.category}
            📊 <b>Status Lama:</b> $oldStatus
            🚀 <b>Status Baru:</b> <b>$statusEmoji</b>
            ${if (ticket.adminNotes.isNotEmpty()) "💬 <b>Catatan Admin:</b> <i>" + ticket.adminNotes + "</i>\n" else ""}━━━━━━━━━━━━━━━━━━━━
            🤖 <i>Notifikasi otomatis Admin Backoffice Bot</i>
        """.trimIndent()

        val adminResult = sendMessage(botToken, adminId, text)
        if (groupId.isNotBlank()) {
            sendMessage(botToken, groupId, text)
        }
        return adminResult
    }

    // Set Telegram Webhook (e.g. for Cloudflare Workers 24/7 Free Hosting)
    suspend fun setWebhook(
        botToken: String,
        webhookUrl: String,
        secretToken: String? = null,
        dropPendingUpdates: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        val trimmedUrl = webhookUrl.trim()
        if (trimmedToken.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token bot belum diatur"))
        if (trimmedUrl.isEmpty() || !trimmedUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("URL Webhook harus berawalan HTTPS valid (contoh: https://worker.domain.workers.dev/webhook)"))
        }

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/setWebhook"
            val payload = JSONObject().apply {
                put("url", trimmedUrl)
                put("drop_pending_updates", dropPendingUpdates)
                if (!secretToken.isNullOrEmpty()) {
                    put("secret_token", secretToken.trim())
                }
            }

            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful && respBody.isNotEmpty()) {
                val json = JSONObject(respBody)
                if (json.optBoolean("ok", false)) {
                    Result.success(json.optString("description", "Webhook Telegram berhasil diarahkan ke Cloudflare!"))
                } else {
                    Result.failure(Exception(json.optString("description", "Gagal mengatur Webhook")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: Gagal mengatur Webhook Telegram"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get Current Webhook Info
    suspend fun getWebhookInfo(botToken: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token bot belum diatur"))

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/getWebhookInfo"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful && respBody.isNotEmpty()) {
                val json = JSONObject(respBody)
                if (json.optBoolean("ok", false)) {
                    val resultObj = json.optJSONObject("result") ?: JSONObject()
                    Result.success(resultObj)
                } else {
                    Result.failure(Exception(json.optString("description", "Gagal mendapatkan Webhook Info")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: Gagal mendapatkan Webhook Info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Delete Telegram Webhook (to switch back to mobile poller)
    suspend fun deleteWebhook(botToken: String, dropPendingUpdates: Boolean = true): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = botToken.trim()
        if (trimmedToken.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Token bot belum diatur"))

        try {
            val url = "https://api.telegram.org/bot$trimmedToken/deleteWebhook"
            val payload = JSONObject().apply {
                put("drop_pending_updates", dropPendingUpdates)
            }
            val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (response.isSuccessful && respBody.isNotEmpty()) {
                val json = JSONObject(respBody)
                if (json.optBoolean("ok", false)) {
                    Result.success("Webhook berhasil dihapus! Sekarang mode long-polling aktif kembali.")
                } else {
                    Result.failure(Exception(json.optString("description", "Gagal menghapus Webhook")))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code}: Gagal menghapus Webhook"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun openTelegramShareIntent(context: Context, text: String) {
            try {
                val encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/share/url?url=$encodedText"))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Laporan Telegram"))
            }
        }

        fun openTelegramChat(context: Context, usernameOrId: String) {
            try {
                val cleanUser = usernameOrId.removePrefix("@").removePrefix("https://t.me/")
                val uri = Uri.parse("https://t.me/$cleanUser")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        fun openTelegramGroup(context: Context, groupLink: String) {
            try {
                val targetUrl = if (groupLink.startsWith("http")) groupLink else "https://t.me/$groupLink"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
