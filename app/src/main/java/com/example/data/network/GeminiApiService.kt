package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

class GeminiApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val systemInstruction = """
        Anda adalah Asisten Cerdas Spesialis Backoffice Ops, Domain & Telegram Dispatcher.
        Tugas Anda:
        1. Membantu analisis kendala member (akses web, DNS terblokir Nawala / Internet Positif, gagal login, deposit/WD, marketing tag).
        2. Memberikan rekomendasi teknis untuk domain (setting DNS Cloudflare, SSL, CNAME, A-Record, sitemap, robots.txt).
        3. Memandu verifikasi dan index ulang di Google Search Console (URL Inspection, Request Indexing, Marketing Tags /backoffice/settings/marketing-tags).
        4. Menyusun draft laporan Telegram yang rapi, profesional, berstruktur dengan emoji jelas untuk Admin dan Grup Telegram.
        Format jawaban ringkas, jelas, praktis, dan dapat langsung dieksekusi.
    """.trimIndent()

    suspend fun generateChatResponse(
        apiKey: String,
        history: List<ChatMessage>,
        userPrompt: String,
        modelName: String = "gemini-2.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API Key belum terkonfigurasi. Silakan masukkan API Key di menu Pengaturan atau melalui Secrets panel di AI Studio.")
            )
        }

        try {
            // Models to try in order of preference
            val models = listOf(modelName, "gemini-2.5-flash", "gemini-2.0-flash")
            var lastError: Exception? = null

            for (model in models) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$trimmedKey"

                    val rootJson = JSONObject()

                    // System instruction
                    val systemObj = JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    }
                    rootJson.put("system_instruction", systemObj)

                    // History + new prompt
                    val contentsArray = JSONArray()
                    for (msg in history.takeLast(10)) {
                        val msgObj = JSONObject().apply {
                            put("role", if (msg.role == "user") "user" else "model")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", msg.content) })
                            })
                        }
                        contentsArray.put(msgObj)
                    }

                    // Add current message
                    contentsArray.put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userPrompt) })
                        })
                    })

                    rootJson.put("contents", contentsArray)

                    // Generation config
                    val genConfig = JSONObject().apply {
                        put("temperature", 0.7)
                        put("topK", 40)
                        put("topP", 0.95)
                        put("maxOutputTokens", 2048)
                    }
                    rootJson.put("generationConfig", genConfig)

                    val body = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder().url(url).post(body).build()

                    val response = client.newCall(request).execute()
                    val responseText = response.body?.string().orEmpty()

                    if (response.isSuccessful && responseText.isNotEmpty()) {
                        val parsed = JSONObject(responseText)
                        val candidates = parsed.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val contentObj = firstCandidate.optJSONObject("content")
                            val parts = contentObj?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val replyText = parts.getJSONObject(0).optString("text")
                                if (replyText.isNotBlank()) {
                                    return@withContext Result.success(replyText)
                                }
                            }
                        }
                    } else {
                        val errObj = try { JSONObject(responseText).optJSONObject("error") } catch (e: Exception) { null }
                        val errMsg = errObj?.optString("message") ?: "HTTP ${response.code}: ${response.message}"
                        lastError = Exception(errMsg)
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }

            Result.failure(lastError ?: Exception("Gagal mendapatkan respon dari Gemini AI"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
