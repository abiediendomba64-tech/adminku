package com.example.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "CloudflareDeployService"

data class CloudflareDeployResult(
    val success: Boolean,
    val scriptName: String,
    val message: String,
    val workerUrl: String? = null
)

class CloudflareDeployService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun deployRemoteWorker(
        apiToken: String,
        accountId: String,
        scriptName: String = "backoffice-telegram-bot",
        workerCode: String,
        subdomain: String = ""
    ): CloudflareDeployResult = withContext(Dispatchers.IO) {
        val trimmedToken = apiToken.trim()
        val trimmedAccount = accountId.trim()

        if (trimmedToken.isEmpty() || trimmedAccount.isEmpty()) {
            return@withContext CloudflareDeployResult(
                success = false,
                scriptName = scriptName,
                message = "Cloudflare API Token dan Account ID wajib diisi untuk remote deployment."
            )
        }

        try {
            // Cloudflare Workers API endpoint for deploying script
            val url = "https://api.cloudflare.com/client/v4/accounts/$trimmedAccount/workers/scripts/$scriptName"
            val requestBody = workerCode.toRequestBody("application/javascript; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Authorization", "Bearer $trimmedToken")
                .header("Content-Type", "application/javascript")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val code = response.code

            Log.d(TAG, "Cloudflare API Deploy Response: HTTP $code, $responseBody")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val isSuccess = json.optBoolean("success", false)
                if (isSuccess) {
                    val finalUrl = if (subdomain.isNotBlank()) {
                        "https://$scriptName.$subdomain.workers.dev/webhook"
                    } else {
                        "https://$scriptName.workers.dev/webhook"
                    }
                    CloudflareDeployResult(
                        success = true,
                        scriptName = scriptName,
                        message = "🚀 Berhasil Deploy Remote ke Cloudflare Workers!\nWorker aktif di Cloudflare Edge.",
                        workerUrl = finalUrl
                    )
                } else {
                    val errors = json.optJSONArray("errors")?.toString() ?: "Gagal deploy script"
                    CloudflareDeployResult(false, scriptName, "Error Cloudflare: $errors")
                }
            } else {
                CloudflareDeployResult(false, scriptName, "HTTP $code: Gagal deploy ke Cloudflare. Periksa API Token & Account ID Anda.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote deploy exception", e)
            CloudflareDeployResult(false, scriptName, "Terjadi kesalahan koneksi: ${e.message}")
        }
    }
}
