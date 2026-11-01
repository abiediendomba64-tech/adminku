package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.network.TelegramApiService
import com.example.data.prefs.AppSettings
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusPending
import com.example.ui.theme.TelegramBrandColor
import com.example.ui.viewmodel.TelegramUiStatus

@Composable
fun TelegramReportScreen(
    settings: AppSettings,
    telegramStatus: TelegramUiStatus,
    onSendReportToAdmin: (String) -> Unit,
    onSendReportToGroup: (String) -> Unit,
    onSendGscReport: () -> Unit,
    onResetStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    reportTextGenerator: (includeDomains: Boolean, includeTickets: Boolean, onlyOpen: Boolean) -> String
) {
    val context = LocalContext.current
    var includeDomains by remember { mutableStateOf(true) }
    var includeTickets by remember { mutableStateOf(true) }
    var onlyOpenTickets by remember { mutableStateOf(false) }

    val currentReport = remember(includeDomains, includeTickets, onlyOpenTickets, settings) {
        reportTextGenerator(includeDomains, includeTickets, onlyOpenTickets)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Result Banner (Success or Error)
        item {
            when (telegramStatus) {
                is TelegramUiStatus.Sending -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Mengirim laporan ke server Telegram API...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                is TelegramUiStatus.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StatusActive.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusActive)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Berhasil Terkirim!",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = StatusActive
                                    )
                                    Text(
                                        text = "${telegramStatus.message} (Message ID: ${telegramStatus.messageId})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                            IconButton(onClick = onResetStatus) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tutup")
                            }
                        }
                    }
                }
                is TelegramUiStatus.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StatusBlocked.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = StatusBlocked)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Gagal Mengirim via Bot API",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = StatusBlocked
                                    )
                                    Text(
                                        text = telegramStatus.errorMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                            IconButton(onClick = onResetStatus) {
                                Icon(Icons.Default.Refresh, contentDescription = "Tutup")
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        // Header: Target Telegram Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TelegramBrandColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Target Pengiriman Telegram",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Admin & Grup Sesuai Permintaan",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Edit Target")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Target 1: Admin User 7862805424 / felixsnd
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                TelegramApiService.openTelegramChat(context, settings.telegramAdminUsername)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = TelegramBrandColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Admin: @${settings.telegramAdminUsername}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Chat ID: ${settings.telegramAdminId}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Buka di Telegram",
                            modifier = Modifier.size(16.dp),
                            tint = TelegramBrandColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Target 2: Group https://t.me/+ybOzZ_lstEdhNDU1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                TelegramApiService.openTelegramGroup(context, settings.telegramGroupLink)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Grup Operasional",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = settings.telegramGroupLink,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Buka Grup",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF10B981)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bot Token info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (settings.telegramBotToken.isNotEmpty()) StatusActive else StatusPending
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (settings.telegramBotToken.isNotEmpty()) {
                                "Bot Token Terpasang (Siap Kirim Otomatis)"
                            } else {
                                "Bot Token Belum Diisi (Gunakan Share App atau isi di Pengaturan)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = if (settings.telegramBotToken.isNotEmpty()) StatusActive else StatusPending
                        )
                    }
                }
            }
        }

        // Filter / Customization toggles
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚙️ Komponen Isi Laporan",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sertakan Tiket Kendala Member", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = includeTickets, onCheckedChange = { includeTickets = it })
                    }

                    if (includeTickets) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = onlyOpenTickets, onCheckedChange = { onlyOpenTickets = it })
                            Text("Hanya tiket yang belum selesai (Open & Proses)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sertakan Daftar Request Domain", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = includeDomains, onCheckedChange = { includeDomains = it })
                    }
                }
            }
        }

        // Live Preview of Structured Report
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📄 Pratinjau Format Laporan Terstruktur",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val plainText = currentReport.replace("<b>", "").replace("</b>", "")
                            .replace("<i>", "").replace("</i>", "")
                            .replace("<code>", "").replace("</code>", "")
                        clip.setPrimaryClip(ClipData.newPlainText("Laporan", plainText))
                        Toast.makeText(context, "Laporan disalin!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin Format", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = currentReport.replace("<b>", "").replace("</b>", "")
                            .replace("<i>", "").replace("</i>", "")
                            .replace("<code>", "").replace("</code>", ""),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Action Buttons: Send via Bot API vs Open in Telegram App
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Button 1: Kirim Bot API ke Admin
                Button(
                    onClick = {
                        onSendReportToAdmin(currentReport)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBrandColor)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kirim Bot API ke Admin (${settings.telegramAdminId})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Button 1.5: Kirim Laporan Audit Index GSC
                FilledTonalButton(
                    onClick = onSendGscReport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📊 Kirim Audit Status Index GSC ke Telegram")
                }

                // Button 2: Kirim Bot API ke Group (if group ID configured)
                if (settings.telegramGroupId.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            onSendReportToGroup(currentReport)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kirim Bot API ke Group ID (${settings.telegramGroupId})")
                    }
                }

                // Button 3: Kirim via Aplikasi Telegram (Fallback langsung ke Felix / Share)
                Button(
                    onClick = {
                        val plainText = currentReport.replace("<b>", "").replace("</b>", "")
                            .replace("<i>", "").replace("</i>", "")
                            .replace("<code>", "").replace("</code>", "")
                        TelegramApiService.openTelegramShareIntent(context, plainText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka & Bagikan di Telegram App")
                }

                // Button 4: Chat Langsung ke @felixsnd
                OutlinedButton(
                    onClick = {
                        TelegramApiService.openTelegramChat(context, settings.telegramAdminUsername)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chat Langsung ke @${settings.telegramAdminUsername}")
                }
            }
        }
    }
}
