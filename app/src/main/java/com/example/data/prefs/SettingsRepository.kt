package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val baseDomain: String = "panel-utama88.com",
    val backofficePath: String = "/backoffice",
    val marketingTagsPath: String = "/backoffice/settings/marketing-tags",
    val googleSearchConsoleUrl: String = "https://search.google.com/search-console/welcome",
    val trustPositifUrl: String = "https://trustpositif.app/",
    val reqWebBotUrl: String = "https://t.me/reqweb_bot",
    val telegramBotToken: String = "",
    val telegramAdminId: String = "7862805424",
    val telegramAdminUsername: String = "felixsnd",
    val telegramGroupLink: String = "https://t.me/+ybOzZ_lstEdhNDU1",
    val telegramGroupId: String = "",
    val telegramWebAppUrl: String = "https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app",
    val customGeminiApiKey: String = "",
    val cloudflareWorkerUrl: String = "",
    val githubRepoUrl: String = ""
)

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            baseDomain = prefs.getString("base_domain", "panel-utama88.com") ?: "panel-utama88.com",
            backofficePath = prefs.getString("backoffice_path", "/backoffice") ?: "/backoffice",
            marketingTagsPath = prefs.getString("marketing_tags_path", "/backoffice/settings/marketing-tags") ?: "/backoffice/settings/marketing-tags",
            googleSearchConsoleUrl = prefs.getString("gsc_url", "https://search.google.com/search-console/welcome") ?: "https://search.google.com/search-console/welcome",
            trustPositifUrl = prefs.getString("trustpositif_url", "https://trustpositif.app/") ?: "https://trustpositif.app/",
            reqWebBotUrl = prefs.getString("reqweb_bot_url", "https://t.me/reqweb_bot") ?: "https://t.me/reqweb_bot",
            telegramBotToken = prefs.getString("tg_bot_token", "") ?: "",
            telegramAdminId = prefs.getString("tg_admin_id", "7862805424") ?: "7862805424",
            telegramAdminUsername = prefs.getString("tg_admin_user", "felixsnd") ?: "felixsnd",
            telegramGroupLink = prefs.getString("tg_group_link", "https://t.me/+ybOzZ_lstEdhNDU1") ?: "https://t.me/+ybOzZ_lstEdhNDU1",
            telegramGroupId = prefs.getString("tg_group_id", "") ?: "",
            telegramWebAppUrl = prefs.getString("tg_webapp_url", "https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app") ?: "https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app",
            customGeminiApiKey = prefs.getString("custom_gemini_key", "") ?: "",
            cloudflareWorkerUrl = prefs.getString("cf_worker_url", "") ?: "",
            githubRepoUrl = prefs.getString("github_repo_url", "") ?: ""
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putString("base_domain", newSettings.baseDomain)
            .putString("backoffice_path", newSettings.backofficePath)
            .putString("marketing_tags_path", newSettings.marketingTagsPath)
            .putString("gsc_url", newSettings.googleSearchConsoleUrl)
            .putString("trustpositif_url", newSettings.trustPositifUrl)
            .putString("reqweb_bot_url", newSettings.reqWebBotUrl)
            .putString("tg_bot_token", newSettings.telegramBotToken)
            .putString("tg_admin_id", newSettings.telegramAdminId)
            .putString("tg_admin_user", newSettings.telegramAdminUsername)
            .putString("tg_group_link", newSettings.telegramGroupLink)
            .putString("tg_group_id", newSettings.telegramGroupId)
            .putString("tg_webapp_url", newSettings.telegramWebAppUrl)
            .putString("custom_gemini_key", newSettings.customGeminiApiKey)
            .putString("cf_worker_url", newSettings.cloudflareWorkerUrl)
            .putString("github_repo_url", newSettings.githubRepoUrl)
            .apply()
        _settings.value = newSettings
    }

    fun getEffectiveGeminiApiKey(): String {
        val custom = _settings.value.customGeminiApiKey.trim()
        if (custom.isNotEmpty()) return custom
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }
}
