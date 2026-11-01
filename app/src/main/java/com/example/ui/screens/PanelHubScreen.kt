package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.TelegramApiService
import com.example.data.prefs.AppSettings
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.TelegramBrandColor
import com.example.ui.viewmodel.ReindexUiStatus

@Composable
fun PanelHubScreen(
    settings: AppSettings,
    reindexStatus: ReindexUiStatus,
    onTriggerReindex: (String) -> Unit,
    onDismissReindex: () -> Unit,
    onOpenUrlInBrowser: (url: String, title: String) -> Unit,
    onOpenExternalBrowser: (url: String) -> Unit,
    onNavigateToChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateDomain: (String) -> Unit
) {
    val context = LocalContext.current
    var editingDomain by remember { mutableStateOf(false) }
    var tempDomain by remember { mutableStateOf(settings.baseDomain) }
    var showConfirmReindexDialog by remember { mutableStateOf(false) }
    var reindexTargetDomain by remember { mutableStateOf(settings.baseDomain) }

    val backofficeUrl = "https://${settings.baseDomain}${settings.backofficePath}"
    val marketingTagsUrl = "https://${settings.baseDomain}${settings.marketingTagsPath}"
    val gscUrl = settings.googleSearchConsoleUrl

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header: Base Domain & Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    TelegramBrandColor.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "PANEL DOMAIN AKTIF",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.2.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(StatusActive)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Server Online & Siap Index",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Pengaturan",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (editingDomain) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = tempDomain,
                                    onValueChange = { tempDomain = it.replace("https://", "").trim() },
                                    label = { Text("Ubah Domain") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (tempDomain.isNotBlank()) {
                                            onUpdateDomain(tempDomain)
                                            reindexTargetDomain = tempDomain
                                            editingDomain = false
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Simpan", tint = StatusActive)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = settings.baseDomain,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, settings.baseDomain, "Domain disalin")
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Salin",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            tempDomain = settings.baseDomain
                                            editingDomain = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Akses Langsung Panel & Google Search Console
        item {
            Text(
                text = "🚀 Akses Cepat Panel & Search Console",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Card 1: Masuk Panel /backoffice
        item {
            PanelActionCard(
                title = "Masuk Panel /backoffice",
                description = "Buka antarmuka login & dashboard backoffice utama untuk mengelola member dan sistem.",
                url = backofficeUrl,
                icon = Icons.Default.Domain,
                badgeColor = MaterialTheme.colorScheme.primary,
                badgeText = "DASHBOARD UTAMA",
                onOpenInApp = { onOpenUrlInBrowser(backofficeUrl, "Panel /backoffice") },
                onOpenExternal = { onOpenExternalBrowser(backofficeUrl) },
                onCopy = { copyToClipboard(context, backofficeUrl, "URL Backoffice disalin") }
            )
        }

        // Card 2: Settingan Panel Index Ulang /backoffice/settings/marketing-tags & TRIGGER RE-INDEX
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Tag, contentDescription = null, tint = Color(0xFF8B5CF6))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Index Ulang: Marketing Tags",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "GSC RE-INDEXING & TAGS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF8B5CF6)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { copyToClipboard(context, marketingTagsUrl, "URL Marketing Tags disalin") }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Kelola tag marketing, GTM, meta tag verifikasi Google Console di path /backoffice/settings/marketing-tags, dan picu proses Re-indexing domain secara otomatis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = marketingTagsUrl,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // TRIGGER RE-INDEX ACTION BUTTON (FEATURE REQUESTED)
                    Button(
                        onClick = {
                            reindexTargetDomain = settings.baseDomain
                            showConfirmReindexDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⚡ Trigger Re-indexing GSC Domain Ini", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onOpenUrlInBrowser(marketingTagsUrl, "Marketing Tags Settings") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka di App", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onOpenExternalBrowser(marketingTagsUrl) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chrome", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Card 3: Google Search Console Welcome
        item {
            PanelActionCard(
                title = "Google Search Console Index",
                description = "Inspeksi URL baru, kirim permintaan index ulang (Request Indexing), periksa sitemap, dan monitor perayapan Google Bot.",
                url = gscUrl,
                icon = Icons.Default.Search,
                badgeColor = Color(0xFFEA4335),
                badgeText = "GOOGLE CONSOLE",
                onOpenInApp = { onOpenUrlInBrowser(gscUrl, "Google Search Console") },
                onOpenExternal = { onOpenExternalBrowser(gscUrl) },
                onCopy = { copyToClipboard(context, gscUrl, "URL Google Search Console disalin") }
            )
        }

        // Card 4: Cek Database TrustPositif (Kominfo / Nawala)
        item {
            PanelActionCard(
                title = "Cek TrustPositif (Kominfo / Nawala)",
                description = "Cek apakah domain utama atau alternatif terdaftar di blacklist Nawala / Internet Positif Kominfo.",
                url = settings.trustPositifUrl,
                icon = Icons.Default.Shield,
                badgeColor = Color(0xFFEF4444),
                badgeText = "TRUSTPOSITIF KOMINFO",
                onOpenInApp = { onOpenUrlInBrowser(settings.trustPositifUrl, "Cek TrustPositif") },
                onOpenExternal = { onOpenExternalBrowser(settings.trustPositifUrl) },
                onCopy = { copyToClipboard(context, settings.trustPositifUrl, "URL TrustPositif disalin") }
            )
        }

        // Card 5: Integrasi Bot @reqweb_bot (Request Web & Landing Page)
        item {
            PanelActionCard(
                title = "Integrasi Bot @reqweb_bot",
                description = "Layanan bot Telegram terintegrasi untuk pengajuan request pembuatan domain, mirror, dan landing page.",
                url = settings.reqWebBotUrl,
                icon = Icons.Default.SmartToy,
                badgeColor = Color(0xFF0284C7),
                badgeText = "REQWEB BOT TELEGRAM",
                onOpenInApp = { onOpenUrlInBrowser(settings.reqWebBotUrl, "ReqWeb Bot") },
                onOpenExternal = {
                    TelegramApiService.openTelegramChat(context, "reqweb_bot")
                },
                onCopy = { copyToClipboard(context, settings.reqWebBotUrl, "Tautan @reqweb_bot disalin") }
            )
        }

        // AI Guidance Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Asisten Re-indexing & Bot Telegram",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Gunakan perintah bot /reindex <domain> di Telegram atau klik tombol trigger untuk audit dan laporan otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Checklist Verifikasi & Troubleshooting
        item {
            Text(
                text = "📌 Panduan Verifikasi & Index Ulang Domain",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ChecklistRow(
                        num = "1",
                        title = "Trigger Index Ulang di Panel",
                        desc = "Tekan 'Trigger Re-indexing GSC' atau jalankan perintah bot Telegram '/reindex nama-domain.com'."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ChecklistRow(
                        num = "2",
                        title = "Audit Meta Tag & Marketing Tags",
                        desc = "Sistem secara otomatis memeriksa tag HTML verifikasi pada /backoffice/settings/marketing-tags."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ChecklistRow(
                        num = "3",
                        title = "Laporan Otomatis Terkirim ke Telegram",
                        desc = "Hasil sukses/gagal langsung dilaporkan ke Admin (ID 7862805424) dan Grup Telegram @felixsnd."
                    )
                }
            }
        }
    }

    // CONFIRM RE-INDEX DIALOG
    if (showConfirmReindexDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmReindexDialog = false },
            icon = { Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF8B5CF6)) },
            title = {
                Text(
                    text = "Konfirmasi Re-Index Domain GSC",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Apakah Anda ingin memicu proses Re-indexing Google Search Console untuk domain berikut?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reindexTargetDomain,
                        onValueChange = { reindexTargetDomain = it.replace("https://", "").replace("http://", "").trim() },
                        label = { Text("Nama Domain") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ℹ️ Hasil eksekusi (Sukses/Gagal) akan langsung dikirim dalam format terstruktur ke Admin (ID ${settings.telegramAdminId}) dan grup Telegram.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmReindexDialog = false
                        onTriggerReindex(reindexTargetDomain)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text("Eksekusi & Kirim Laporan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmReindexDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // RE-INDEX EXECUTION STATUS MODAL
    when (reindexStatus) {
        is ReindexUiStatus.Loading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("⏳ Sedang Memproses Re-indexing...") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Mengaudit tag verifikasi marketing-tags & mengirim sinyal indexing ke Googlebot untuk domain ${reindexStatus.domain}...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is ReindexUiStatus.Result -> {
            val res = reindexStatus.executionResult
            AlertDialog(
                onDismissRequest = onDismissReindex,
                icon = {
                    Icon(
                        imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (res.success) StatusActive else StatusBlocked,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = if (res.success) "✅ Re-indexing Berhasil Dikirim!" else "❌ Re-indexing Gagal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = res.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tracking ID: ${res.gscTrackingId}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Laporan lengkap telah dikirim ke Admin ID ${settings.telegramAdminId} dan Grup Telegram.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismissReindex,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Selesai")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            TelegramApiService.openTelegramShareIntent(context, res.reportText)
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buka di Telegram")
                    }
                }
            )
        }
        else -> {}
    }
}

@Composable
fun PanelActionCard(
    title: String,
    description: String,
    url: String,
    icon: ImageVector,
    badgeColor: Color,
    badgeText: String,
    onOpenInApp: () -> Unit,
    onOpenExternal: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = badgeColor)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = badgeColor
                            )
                        }
                    }
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Salin URL",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onOpenInApp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buka di App", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenExternal,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chrome", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ChecklistRow(num: String, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = num,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("URL", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
