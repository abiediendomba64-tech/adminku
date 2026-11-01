package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.AppSettings
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusPending
import com.example.ui.theme.TelegramBrandColor

@Composable
fun CloudFreeTierScreen(
    settings: AppSettings,
    webhookResult: String?,
    isLoading: Boolean,
    onSetWebhook: (String) -> Unit,
    onGetWebhookInfo: () -> Unit,
    onDeleteWebhook: () -> Unit,
    onClearResult: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    var workerUrlInput by remember(settings.cloudflareWorkerUrl) {
        mutableStateOf(if (settings.cloudflareWorkerUrl.isNotEmpty()) settings.cloudflareWorkerUrl else "https://backoffice-telegram-bot.your-subdomain.workers.dev/webhook")
    }
    var githubRepoInput by remember(settings.githubRepoUrl) {
        mutableStateOf(if (settings.githubRepoUrl.isNotEmpty()) settings.githubRepoUrl else "https://github.com/username/backoffice-bot")
    }

    fun copyToClipboard(label: String, text: String) {
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label disalin ke clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuka link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val workerJsSample = """
// Cloudflare Worker Telegram Bot (Free Tier: 100k req/day)
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method === "POST" && (url.pathname === "/webhook" || url.pathname === "/")) {
      const update = await request.json();
      // Handle commands: /start, /panel, /reindex, /ticket, /report
      // Admin: 7862805424 (@felixsnd), Group: https://t.me/+ybOzZ_lstEdhNDU1
      return new Response("OK", { status: 200 });
    }
    return new Response("Backoffice Bot Online", { status: 200 });
  }
};
    """.trimIndent()

    val githubWorkflowSample = """
name: Build Android APK (Free CI/CD)
on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: BackofficeBot-Debug-APK
          path: app/build/outputs/apk/debug/*.apk
    """.trimIndent()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD 1: OVERVIEW FREE TIER ECOSYSTEM
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF6821F).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFF6821F))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Integrasi Cloudflare & GitHub",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = StatusActive.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "100% GRATIS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = StatusActive,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Hosting Serverless 24/7 & Build APK Otomatis Free Tier",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Arsitektur lengkap tanpa biaya operasional (zero-cost). Bot Telegram berjalan non-stop di Cloudflare Edge, dan file APK Android di-build otomatis di cloud oleh GitHub Actions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF6821F).copy(alpha = 0.1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("☁️ Cloudflare", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFD97706))
                                Text("100.000 req/hari", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                                Text("Serverless 0ms cold start", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF24292F).copy(alpha = 0.1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("🐙 GitHub Actions", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1E293B))
                                Text("2.000 menit/bln", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                                Text("Free Ubuntu Runner CI/CD", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // FEEDBACK BANNER (IF ANY)
        if (!webhookResult.isNullOrEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (webhookResult.contains("✅")) StatusActive.copy(alpha = 0.15f) else StatusBlocked.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = webhookResult,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearResult) {
                            Icon(Icons.Default.Refresh, contentDescription = "Tutup")
                        }
                    }
                }
            }
        }

        // CARD 2: CLOUDFLARE WORKERS WEBHOOK 24/7
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFFF6821F))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloudflare Workers Webhook (Bot 24/7)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Arahkan webhook bot Telegram langsung ke Cloudflare Worker. Bot akan merespons perintah /reindex, /ticket, /panel, dan /report secara instan tanpa perlu HP Anda aktif.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = workerUrlInput,
                        onValueChange = { workerUrlInput = it },
                        label = { Text("URL Cloudflare Worker Webhook") },
                        placeholder = { Text("https://your-worker.workers.dev/webhook") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                copyToClipboard("URL Worker", workerUrlInput)
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(16.dp))
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menghubungkan ke API Telegram...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Button(
                            onClick = {
                                onSaveSettings(settings.copy(cloudflareWorkerUrl = workerUrlInput))
                                onSetWebhook(workerUrlInput)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6821F))
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("⚡ Pasang Webhook ke Cloudflare (setWebhook)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onGetWebhookInfo,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cek Status Webhook", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onDeleteWebhook,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hapus Webhook", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard("worker.js", workerJsSample)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin worker.js", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                openUrl("https://dash.cloudflare.com/")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buka Cloudflare", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // CARD 3: GITHUB ACTIONS BUILD APK GRATIS (CI/CD)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GitHub Actions Build APK Gratis (CI/CD)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Build file APK Android otomatis di server cloud GitHub secara gratis. Anda tidak perlu menginstall Android Studio atau menggunakan laptop spek tinggi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubRepoInput,
                        onValueChange = {
                            githubRepoInput = it
                            onSaveSettings(settings.copy(githubRepoUrl = it))
                        },
                        label = { Text("Link Repository GitHub Anda") },
                        placeholder = { Text("https://github.com/username/backoffice-bot") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "📖 4 Langkah Mudah Download APK Gratis:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val steps = listOf(
                        "1. Push kode proyek ke GitHub (Public atau Private gratis).",
                        "2. Masuk ke tab 'Actions' di repository GitHub Anda.",
                        "3. Klik workflow 'Build Android APK (Free CI/CD)' lalu klik 'Run workflow'.",
                        "4. Setelah selesai (2-3 menit), scroll ke bawah dan unduh 'BackofficeBot-Debug-APK'!"
                    )

                    steps.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                copyToClipboard("build-apk.yml", githubWorkflowSample)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin build-apk.yml", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = {
                                val url = if (githubRepoInput.startsWith("http")) githubRepoInput else "https://github.com"
                                openUrl(url)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka GitHub Repo", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // CARD 4: CHEAT SHEET PERINTAH BOT FREE TIER
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kelengkapan Bot Free Tier (Cheat Sheet)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Semua perintah di bawah siap diproses 24/7 di Cloudflare Worker dan poller lokal:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    val commands = listOf(
                        "/start" to "Sambutan & tombol buka Web App di chat",
                        "/panel" to "Link langsung ke /backoffice & marketing tags",
                        "/reindex <domain>" to "Audit HTTP, verification tag GSC & sitemap",
                        "/ticket <kendala>" to "Auto-assign tiket #TKT-XXXX & alert admin",
                        "/status_ticket <id> <status>" to "Update status penanganan kendala",
                        "/trustpositif <domain>" to "Cek database blokir Kominfo & @reqweb_bot",
                        "/list_domains" to "Daftar domain aktif & status GSC",
                        "/report" to "Kirim laporan ke Admin 7862805424 & Grup"
                    )

                    commands.forEach { (cmd, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cmd,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
