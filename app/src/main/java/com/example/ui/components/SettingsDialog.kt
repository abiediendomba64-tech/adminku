package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.prefs.AppSettings

@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    botVerificationStatus: String?,
    onVerifyToken: (String) -> Unit,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var baseDomain by remember { mutableStateOf(currentSettings.baseDomain) }
    var backofficePath by remember { mutableStateOf(currentSettings.backofficePath) }
    var marketingTagsPath by remember { mutableStateOf(currentSettings.marketingTagsPath) }
    var tgBotToken by remember { mutableStateOf(currentSettings.telegramBotToken) }
    var tgAdminId by remember { mutableStateOf(currentSettings.telegramAdminId) }
    var tgAdminUser by remember { mutableStateOf(currentSettings.telegramAdminUsername) }
    var tgGroupLink by remember { mutableStateOf(currentSettings.telegramGroupLink) }
    var tgGroupId by remember { mutableStateOf(currentSettings.telegramGroupId) }
    var tgWebAppUrl by remember { mutableStateOf(currentSettings.telegramWebAppUrl) }
    var customGeminiKey by remember { mutableStateOf(currentSettings.customGeminiApiKey) }
    var cloudflareWorkerUrl by remember { mutableStateOf(currentSettings.cloudflareWorkerUrl) }
    var githubRepoUrl by remember { mutableStateOf(currentSettings.githubRepoUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚙️ Pengaturan Integrasi & Panel",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section Panel Backoffice
                Text(
                    text = "🌐 Konfigurasi Domain Panel",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = baseDomain,
                    onValueChange = { baseDomain = it.replace("https://", "").replace("http://", "").trim() },
                    label = { Text("Nama Domain Utama") },
                    placeholder = { Text("misal: panel-utama88.com") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = backofficePath,
                        onValueChange = { backofficePath = it },
                        label = { Text("Path Backoffice") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = marketingTagsPath,
                        onValueChange = { marketingTagsPath = it },
                        label = { Text("Path Marketing Tags") },
                        modifier = Modifier.weight(1.3f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section Telegram
                Text(
                    text = "🤖 Integrasi Bot Telegram",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dapatkan Bot Token gratis dari @BotFather di Telegram untuk mengirim laporan otomatis via API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = tgBotToken,
                    onValueChange = { tgBotToken = it.trim() },
                    label = { Text("Telegram Bot Token") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (tgBotToken.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onVerifyToken(tgBotToken) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifikasi Token Bot")
                    }
                    if (!botVerificationStatus.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = botVerificationStatus,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tgAdminId,
                        onValueChange = { tgAdminId = it.trim() },
                        label = { Text("Admin Chat ID") },
                        placeholder = { Text("7862805424") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = tgAdminUser,
                        onValueChange = { tgAdminUser = it.replace("@", "").trim() },
                        label = { Text("Admin Username") },
                        placeholder = { Text("felixsnd") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tgGroupLink,
                    onValueChange = { tgGroupLink = it.trim() },
                    label = { Text("Tautan Grup Telegram") },
                    placeholder = { Text("https://t.me/+ybOzZ_lstEdhNDU1") },
                    leadingIcon = { Icon(Icons.Default.Send, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tgGroupId,
                    onValueChange = { tgGroupId = it.trim() },
                    label = { Text("Group Chat ID (Opsional untuk Bot)") },
                    placeholder = { Text("-100xxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tgWebAppUrl,
                    onValueChange = { tgWebAppUrl = it.trim() },
                    label = { Text("Telegram Web App URL") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section Gemini AI
                Text(
                    text = "✨ Gemini AI Intelligence",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Konfigurasikan kunci Gemini API untuk asisten pintar troubleshooting & analitik kendala.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = customGeminiKey,
                    onValueChange = { customGeminiKey = it.trim() },
                    label = { Text("Custom Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Section Cloud & Free Tier
                Text(
                    text = "☁️ Cloudflare & GitHub Free Tier",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = cloudflareWorkerUrl,
                    onValueChange = { cloudflareWorkerUrl = it.trim() },
                    label = { Text("Cloudflare Worker Webhook URL") },
                    placeholder = { Text("https://your-worker.workers.dev/webhook") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = githubRepoUrl,
                    onValueChange = { githubRepoUrl = it.trim() },
                    label = { Text("GitHub Repo URL (Free CI/CD APK)") },
                    placeholder = { Text("https://github.com/username/backoffice-bot") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentSettings.copy(
                            baseDomain = if (baseDomain.isBlank()) "panel-utama88.com" else baseDomain,
                            backofficePath = if (backofficePath.isBlank()) "/backoffice" else backofficePath,
                            marketingTagsPath = if (marketingTagsPath.isBlank()) "/backoffice/settings/marketing-tags" else marketingTagsPath,
                            telegramBotToken = tgBotToken,
                            telegramAdminId = if (tgAdminId.isBlank()) "7862805424" else tgAdminId,
                            telegramAdminUsername = if (tgAdminUser.isBlank()) "felixsnd" else tgAdminUser,
                            telegramGroupLink = if (tgGroupLink.isBlank()) "https://t.me/+ybOzZ_lstEdhNDU1" else tgGroupLink,
                            telegramGroupId = tgGroupId,
                            telegramWebAppUrl = if (tgWebAppUrl.isBlank()) "https://ais-pre-3jtqjse2yo4oegu26drysb-313938030691.asia-southeast1.run.app" else tgWebAppUrl,
                            customGeminiApiKey = customGeminiKey,
                            cloudflareWorkerUrl = cloudflareWorkerUrl,
                            githubRepoUrl = githubRepoUrl
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Simpan Pengaturan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
