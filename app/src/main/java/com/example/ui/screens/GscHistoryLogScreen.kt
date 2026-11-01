@file:Suppress("DEPRECATION")

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.data.model.GscSubmissionLog
import com.example.data.network.GscService
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusPending
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GscHistoryLogScreen(
    logs: List<GscSubmissionLog>,
    onBack: () -> Unit,
    onSubmitUrl: (url: String, engine: String) -> Unit,
    onDeleteLog: (GscSubmissionLog) -> Unit,
    onClearAllLogs: () -> Unit,
    onSendReportToTelegram: (String) -> Unit,
    onOpenInBrowser: (String, String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var selectedEngineFilter by remember { mutableStateOf("ALL") }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedLogForDetail by remember { mutableStateOf<GscSubmissionLog?>(null) }

    fun copyToClipboard(text: String, label: String = "Teks") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label disalin ke clipboard", Toast.LENGTH_SHORT).show()
    }

    val filteredLogs = remember(logs, searchQuery, selectedStatusFilter, selectedEngineFilter) {
        logs.filter { log ->
            val matchesQuery = searchQuery.isBlank() ||
                    log.url.contains(searchQuery, ignoreCase = true) ||
                    log.domain.contains(searchQuery, ignoreCase = true) ||
                    log.gscTrackingId.contains(searchQuery, ignoreCase = true)

            val matchesStatus = when (selectedStatusFilter) {
                "INDEXED" -> log.indexingStatus == GscSubmissionLog.STATUS_INDEXED
                "SUBMITTED" -> log.indexingStatus == GscSubmissionLog.STATUS_SUBMITTED
                "VERIFIED" -> log.indexingStatus == GscSubmissionLog.STATUS_VERIFIED_OWNERSHIP
                "PENDING" -> log.indexingStatus == GscSubmissionLog.STATUS_PENDING_CRAWL
                "ERROR" -> log.indexingStatus == GscSubmissionLog.STATUS_ERROR || log.indexingStatus == "UNREACHABLE"
                else -> true
            }

            val matchesEngine = when (selectedEngineFilter) {
                "ALL" -> true
                else -> log.searchEngine.contains(selectedEngineFilter, ignoreCase = true)
            }

            matchesQuery && matchesStatus && matchesEngine
        }
    }

    val totalCount = logs.size
    val indexedCount = logs.count { it.indexingStatus == GscSubmissionLog.STATUS_INDEXED }
    val submittedCount = logs.count { it.indexingStatus == GscSubmissionLog.STATUS_SUBMITTED }
    val verifiedCount = logs.count { it.indexingStatus == GscSubmissionLog.STATUS_VERIFIED_OWNERSHIP }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Riwayat Log Indexing GSC",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$totalCount URL Tercatat • Multi-Search Engine",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showSubmitDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Submit URL Baru")
                    }
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Bersihkan Semua Log",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. STATS BANNER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "📊 Ringkasan Pengajuan URL Indexing",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatChip(label = "Total", count = totalCount, color = MaterialTheme.colorScheme.primary)
                            StatChip(label = "Terindeks", count = indexedCount, color = StatusActive)
                            StatChip(label = "Terkirim", count = submittedCount, color = Color(0xFF3B82F6))
                            StatChip(label = "Terverifikasi", count = verifiedCount, color = Color(0xFF8B5CF6))
                        }
                    }
                }
            }

            // 2. SEARCH & FILTER CONTROLS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari URL, domain, atau ID tracking...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Status Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == "ALL",
                                onClick = { selectedStatusFilter = "ALL" },
                                label = { Text("Semua (${logs.size})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == "INDEXED",
                                onClick = { selectedStatusFilter = "INDEXED" },
                                label = { Text("🟢 Indexed ($indexedCount)") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == "SUBMITTED",
                                onClick = { selectedStatusFilter = "SUBMITTED" },
                                label = { Text("🔵 Submitted ($submittedCount)") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == "VERIFIED",
                                onClick = { selectedStatusFilter = "VERIFIED" },
                                label = { Text("🟣 Terverifikasi ($verifiedCount)") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedStatusFilter == "PENDING",
                                onClick = { selectedStatusFilter = "PENDING" },
                                label = { Text("🟡 Antrean Crawl") }
                            )
                        }
                    }

                    // Search Engine Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedEngineFilter == "ALL",
                                onClick = { selectedEngineFilter = "ALL" },
                                label = { Text("Semua Mesin Pencari") }
                            )
                        }
                        GscService.SUPPORTED_ENGINES.forEach { engine ->
                            item {
                                FilterChip(
                                    selected = selectedEngineFilter == engine.name,
                                    onClick = { selectedEngineFilter = engine.name },
                                    label = { Text(engine.name) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. LOG ITEMS
            if (filteredLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tidak ada riwayat pengajuan yang cocok.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showSubmitDialog = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajukan URL Sekarang")
                            }
                        }
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    GscLogCardItem(
                        log = log,
                        onClick = { selectedLogForDetail = log },
                        onCopyUrl = { copyToClipboard(log.url, "URL") },
                        onResubmit = { onSubmitUrl(log.url, log.searchEngine) },
                        onDelete = { onDeleteLog(log) },
                        onShareToTelegram = {
                            val text = buildGscLogTelegramReport(log)
                            onSendReportToTelegram(text)
                        }
                    )
                }
            }
        }
    }

    // DIALOG: SUBMIT NEW URL
    if (showSubmitDialog) {
        SubmitUrlDialog(
            onDismiss = { showSubmitDialog = false },
            onSubmit = { url, engine ->
                onSubmitUrl(url, engine)
                showSubmitDialog = false
            }
        )
    }

    // DIALOG: CLEAR CONFIRM
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Bersihkan Semua Riwayat Log?") },
            text = { Text("Tindakan ini akan menghapus seluruh catatan riwayat pengajuan URL indexing dari database lokal.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllLogs()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Ya, Bersihkan", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG: DETAIL LOG INSPECTION
    if (selectedLogForDetail != null) {
        GscLogDetailDialog(
            log = selectedLogForDetail!!,
            onDismiss = { selectedLogForDetail = null },
            onResubmit = {
                onSubmitUrl(selectedLogForDetail!!.url, selectedLogForDetail!!.searchEngine)
                selectedLogForDetail = null
            },
            onOpenWebmaster = { url, title ->
                onOpenInBrowser(url, title)
                selectedLogForDetail = null
            },
            onCopyUrl = { copyToClipboard(selectedLogForDetail!!.url, "URL") }
        )
    }
}

@Composable
fun StatChip(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = color
            )
        }
    }
}

@Composable
fun GscLogCardItem(
    log: GscSubmissionLog,
    onClick: () -> Unit,
    onCopyUrl: () -> Unit,
    onResubmit: () -> Unit,
    onDelete: () -> Unit,
    onShareToTelegram: () -> Unit
) {
    val (statusColor, statusLabel) = when (log.indexingStatus) {
        GscSubmissionLog.STATUS_INDEXED -> StatusActive to "🟢 TERINDEKS (INDEXED)"
        GscSubmissionLog.STATUS_SUBMITTED -> MaterialTheme.colorScheme.primary to "🔵 TERKIRIM (SUBMITTED)"
        GscSubmissionLog.STATUS_VERIFIED_OWNERSHIP -> Color(0xFF8B5CF6) to "🟣 KEPEMILIKAN VALID"
        GscSubmissionLog.STATUS_PENDING_CRAWL -> StatusPending to "🟡 ANTREAN CRAWL"
        else -> StatusBlocked to "🔴 BUTUH VERIFIKASI"
    }

    val formattedDate = remember(log.submittedAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(log.submittedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Tracking ID & Engine & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "#${log.gscTrackingId}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.searchEngine,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // URL
            Text(
                text = log.url,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (log.diagnosticMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.diagnosticMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Technical Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "HTTP ${log.httpStatus}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = if (log.verificationTagFound) StatusActive.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (log.verificationTagFound) "Tag GSC: Ada" else "Tag GSC: Belum",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = if (log.verificationTagFound) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = if (log.sitemapDetected) StatusActive.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (log.sitemapDetected) "Sitemap: Valid" else "Sitemap: Tidak",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = if (log.sitemapDetected) StatusActive else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onResubmit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Audit Ulang", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onShareToTelegram,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Telegram", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row {
                    IconButton(onClick = onCopyUrl, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SubmitUrlDialog(
    onDismiss: () -> Unit,
    onSubmit: (url: String, engine: String) -> Unit
) {
    var urlInput by remember { mutableStateOf("https://panel-utama88.com/backoffice/settings/marketing-tags") }
    var selectedEngine by remember { mutableStateOf("Google Search Console") }
    var engineMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajukan URL ke Mesin Pencari") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pilih mesin pencari tujuan dan masukkan URL domain atau sitemap yang ingin diajukan:",
                    style = MaterialTheme.typography.bodySmall
                )

                // Search engine dropdown selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { engineMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedEngine, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("▼", style = MaterialTheme.typography.labelSmall)
                    }

                    DropdownMenu(
                        expanded = engineMenuExpanded,
                        onDismissRequest = { engineMenuExpanded = false }
                    ) {
                        GscService.SUPPORTED_ENGINES.forEach { eng ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(eng.name, fontWeight = FontWeight.Bold)
                                        Text(eng.country, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedEngine = eng.name
                                    engineMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL Target / Sitemap") },
                    placeholder = { Text("https://domain.com/path") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        onSubmit(urlInput.trim(), selectedEngine)
                    }
                },
                enabled = urlInput.isNotBlank()
            ) {
                Text("Ajukan Sekarang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun GscLogDetailDialog(
    log: GscSubmissionLog,
    onDismiss: () -> Unit,
    onResubmit: () -> Unit,
    onOpenWebmaster: (url: String, title: String) -> Unit,
    onCopyUrl: () -> Unit
) {
    val engineMeta = remember(log.searchEngine) {
        GscService.SUPPORTED_ENGINES.firstOrNull { it.name.contains(log.searchEngine, ignoreCase = true) }
            ?: GscService.SUPPORTED_ENGINES.first()
    }

    val formattedDate = remember(log.submittedAt) {
        val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm:ss 'WIB'", Locale.forLanguageTag("id-ID"))
        sdf.format(Date(log.submittedAt))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rincian Audit Indexing")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "URL Target:", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = log.url,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tracking ID: #${log.gscTrackingId} • ${log.searchEngine}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(text = "Waktu Pengajuan: $formattedDate", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(
                    text = "Status: ${log.indexingStatus}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "• Respon Server HTTP: ${log.httpStatus}\n" +
                            "• Meta-Tag Verifikasi: ${if (log.verificationTagFound) "✅ Terdeteksi di Header" else "⚠️ Belum Terdeteksi"}\n" +
                            "• File Sitemap.xml: ${if (log.sitemapDetected) "✅ Ditemukan" else "⚪ Tidak ditemukan / otomatis"}\n\n" +
                            "Rincian Diagnosa:\n${log.diagnosticMessage}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onOpenWebmaster(engineMeta.webmasterUrl, engineMeta.name) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buka Portal Webmaster Resmi", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onResubmit) {
                Text("Ajukan Ulang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

fun buildGscLogTelegramReport(log: GscSubmissionLog): String {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date(log.submittedAt))
    return buildString {
        append("🔍 <b>[AUDIT LOG INDEXING RESMI]</b>\n")
        append("━━━━━━━━━━━━━━━━━━━━\n")
        append("🆔 <b>Tracking Code:</b> <code>#${log.gscTrackingId}</code>\n")
        append("🌐 <b>Target URL:</b> <code>${log.url}</code>\n")
        append("📡 <b>Mesin Pencari:</b> ${log.searchEngine}\n")
        append("📊 <b>Status Indexing:</b> <b>${log.indexingStatus}</b>\n")
        append("⚡ <b>HTTP Status:</b> ${log.httpStatus}\n")
        append("🏷️ <b>Meta Tag GSC:</b> ${if (log.verificationTagFound) "✅ TERVERIFIKASI" else "⚠️ BELUM ADA"}\n")
        append("🗺️ <b>Sitemap.xml:</b> ${if (log.sitemapDetected) "✅ TERSEDIA" else "⚪ MENUNGGU CRAWL"}\n")
        append("📅 <b>Waktu:</b> $date\n")
        append("━━━━━━━━━━━━━━━━━━━━\n")
        append("📝 <i>${log.diagnosticMessage}</i>")
    }
}
