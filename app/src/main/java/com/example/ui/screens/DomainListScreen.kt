package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.model.DomainRequest
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusIndexed
import com.example.ui.theme.StatusPending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DomainListScreen(
    domains: List<DomainRequest>,
    onAddDomain: (name: String, category: String, path: String, dns: String, notes: String) -> Unit,
    onUpdateStatus: (DomainRequest, String) -> Unit,
    onDeleteDomain: (DomainRequest) -> Unit,
    onOpenUrl: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredDomains = domains.filter {
        val matchesFilter = when (selectedFilter) {
            "ACTIVE" -> it.status == DomainRequest.STATUS_ACTIVE
            "PENDING" -> it.status == DomainRequest.STATUS_PENDING
            "INDEXED" -> it.status == DomainRequest.STATUS_INDEXED
            "BLOCKED" -> it.status == DomainRequest.STATUS_BLOCKED
            else -> true
        }
        val matchesSearch = it.domainName.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.notes.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Request Domain")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search & Filter header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama domain atau kategori...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Semua (${domains.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "ACTIVE",
                        onClick = { selectedFilter = "ACTIVE" },
                        label = { Text("Aktif (${domains.count { it.status == DomainRequest.STATUS_ACTIVE }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "INDEXED",
                        onClick = { selectedFilter = "INDEXED" },
                        label = { Text("Indexed GSC (${domains.count { it.status == DomainRequest.STATUS_INDEXED }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "PENDING",
                        onClick = { selectedFilter = "PENDING" },
                        label = { Text("Pending DNS (${domains.count { it.status == DomainRequest.STATUS_PENDING }})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "BLOCKED",
                        onClick = { selectedFilter = "BLOCKED" },
                        label = { Text("Nawala (${domains.count { it.status == DomainRequest.STATUS_BLOCKED }})") }
                    )
                }
            }

            // Domain list
            if (filteredDomains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Domain tidak ditemukan" else "Belum ada request domain",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tekan tombol + di bawah untuk menambahkan domain baru.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDomains, key = { it.id }) { domain ->
                        DomainCardItem(
                            domain = domain,
                            onUpdateStatus = { newStatus -> onUpdateStatus(domain, newStatus) },
                            onDelete = { onDeleteDomain(domain) },
                            onOpenUrl = { onOpenUrl("https://${domain.domainName}${domain.targetPath}", domain.domainName) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDomainDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, cat, path, dns, notes ->
                onAddDomain(name, cat, path, dns, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun DomainCardItem(
    domain: DomainRequest,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMenuOpen by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingPing by remember { mutableStateOf(false) }

    val (badgeColor, statusTitle) = when (domain.status) {
        DomainRequest.STATUS_ACTIVE -> StatusActive to "AKTIF"
        DomainRequest.STATUS_INDEXED -> StatusIndexed to "GSC INDEXED"
        DomainRequest.STATUS_BLOCKED -> StatusBlocked to "NAWALA"
        else -> StatusPending to "PENDING DNS"
    }

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
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = badgeColor)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = domain.domainName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "${domain.category} • Path: ${domain.targetPath}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip with dropdown
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { statusMenuOpen = true },
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = statusMenuOpen,
                        onDismissRequest = { statusMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🟢 Set Aktif") },
                            onClick = {
                                onUpdateStatus(DomainRequest.STATUS_ACTIVE)
                                statusMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⭐ Set GSC Indexed") },
                            onClick = {
                                onUpdateStatus(DomainRequest.STATUS_INDEXED)
                                statusMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⏳ Set Pending DNS") },
                            onClick = {
                                onUpdateStatus(DomainRequest.STATUS_PENDING)
                                statusMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🚫 Set Terblokir Nawala") },
                            onClick = {
                                onUpdateStatus(DomainRequest.STATUS_BLOCKED)
                                statusMenuOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "DNS: ${domain.dnsSettings}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (domain.notes.isNotEmpty()) {
                        Text(
                            text = "Catatan: ${domain.notes}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (pingStatus != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = pingStatus ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = if (pingStatus?.contains("Online") == true) StatusActive else StatusBlocked
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onOpenUrl,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buka URL", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = {
                            isCheckingPing = true
                            pingStatus = "Memeriksa HTTP response..."
                            coroutineScope.launch {
                                val result = testDomainPing("https://${domain.domainName}")
                                pingStatus = result
                                isCheckingPing = false
                            }
                        },
                        enabled = !isCheckingPing,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (isCheckingPing) "Cek..." else "Test DNS", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row {
                    IconButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clip.setPrimaryClip(ClipData.newPlainText("Domain", domain.domainName))
                            Toast.makeText(context, "Domain disalin!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Hapus",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDomainDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, path: String, dns: String, notes: String) -> Unit
) {
    var domainName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Mirror / Alternatif") }
    var targetPath by remember { mutableStateOf("/backoffice") }
    var dnsSettings by remember { mutableStateOf("Cloudflare Proxy (Orange Cloud)") }
    var notes by remember { mutableStateOf("") }

    val categories = listOf("Landing Utama", "Mirror / Alternatif", "Mobile App Redirect", "Marketing Tag")
    val paths = listOf("/backoffice", "/backoffice/settings/marketing-tags")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ Tambah Request Domain Baru", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = domainName,
                    onValueChange = { domainName = it.replace("https://", "").replace("http://", "").trim() },
                    label = { Text("Nama Domain") },
                    placeholder = { Text("contoh: domain-alternatif.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Tipe Domain:", style = MaterialTheme.typography.labelMedium)
                categories.forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { category = cat }
                    ) {
                        RadioButton(selected = category == cat, onClick = { category = cat })
                        Text(cat, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Target Path:", style = MaterialTheme.typography.labelMedium)
                paths.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { targetPath = p }
                    ) {
                        RadioButton(selected = targetPath == p, onClick = { targetPath = p })
                        Text(p, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = dnsSettings,
                    onValueChange = { dnsSettings = it },
                    label = { Text("Setting DNS") },
                    placeholder = { Text("misal: Cloudflare Proxy atau IP VPS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Request Dari") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domainName.isNotBlank()) {
                        onConfirm(domainName, category, targetPath, dnsSettings, notes)
                    }
                },
                enabled = domainName.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

private suspend fun testDomainPing(url: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).head().build()
        val response = client.newCall(request).execute()
        "Online (HTTP ${response.code})"
    } catch (e: Exception) {
        "Unreachable / DNS Pending: ${e.localizedMessage ?: "Timeout"}"
    }
}
