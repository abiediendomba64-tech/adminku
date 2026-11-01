package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DomainRequest
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusPending
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DomainRequestsManager(
    domains: List<DomainRequest>,
    onAddRequest: (name: String, category: String, path: String, dns: String, notes: String) -> Unit,
    onApproveRequest: (DomainRequest) -> Unit,
    onRejectRequest: (DomainRequest, reason: String) -> Unit,
    onDeleteRequest: (DomainRequest) -> Unit,
    onSubmitToGsc: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, APPROVED, REJECTED
    var showAddDialog by remember { mutableStateOf(false) }
    var domainToReject by remember { mutableStateOf<DomainRequest?>(null) }
    var rejectionReasonInput by remember { mutableStateOf("") }

    val filteredDomains = remember(domains, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> domains.filter { it.registrationStatus == DomainRequest.REG_PENDING }
            "APPROVED" -> domains.filter { it.registrationStatus == DomainRequest.REG_APPROVED }
            "REJECTED" -> domains.filter { it.registrationStatus == DomainRequest.REG_REJECTED }
            else -> domains
        }
    }

    val pendingCount = domains.count { it.registrationStatus == DomainRequest.REG_PENDING }
    val approvedCount = domains.count { it.registrationStatus == DomainRequest.REG_APPROVED }
    val rejectedCount = domains.count { it.registrationStatus == DomainRequest.REG_REJECTED }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Filter Bar & Summary
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Domain Requests Manager",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Workflow Registrasi & Persetujuan Domain",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajukan Domain Baru", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Semua (${domains.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == "PENDING",
                        onClick = { selectedFilter = "PENDING" },
                        label = { Text("⏳ Pending ($pendingCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == "APPROVED",
                        onClick = { selectedFilter = "APPROVED" },
                        label = { Text("✅ Disetujui ($approvedCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == "REJECTED",
                        onClick = { selectedFilter = "REJECTED" },
                        label = { Text("❌ Ditolak ($rejectedCount)") }
                    )
                }
            }
        }

        if (filteredDomains.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Domain, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tidak ada permohonan domain dalam kategori ini", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDomains, key = { it.id }) { domain ->
                    DomainRequestCard(
                        domain = domain,
                        onApprove = { onApproveRequest(domain) },
                        onReject = {
                            domainToReject = domain
                            rejectionReasonInput = ""
                        },
                        onDelete = { onDeleteRequest(domain) },
                        onSubmitGsc = { onSubmitToGsc(domain.domainName) }
                    )
                }
            }
        }
    }

    // DIALOG: REJECT REASON
    if (domainToReject != null) {
        AlertDialog(
            onDismissRequest = { domainToReject = null },
            title = { Text("Tolak Permohonan Domain") },
            text = {
                Column {
                    Text("Domain: ${domainToReject?.domainName}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = { Text("Alasan Penolakan") },
                        placeholder = { Text("Contoh: Nama domain bentrok / DNS tidak valid") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = if (rejectionReasonInput.isBlank()) "Ditolak oleh admin" else rejectionReasonInput
                        onRejectRequest(domainToReject!!, reason)
                        domainToReject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusBlocked)
                ) {
                    Text("Tolak Permohonan")
                }
            },
            dismissButton = {
                TextButton(onClick = { domainToReject = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG: ADD NEW REGISTRATION REQUEST
    if (showAddDialog) {
        var newDomain by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("Mirror / Alternatif") }
        var newPath by remember { mutableStateOf("/backoffice") }
        var newDns by remember { mutableStateOf("Cloudflare Proxy (1.1.1.1)") }
        var newNotes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Ajukan Permohonan Domain Baru") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it.replace("https://", "").replace("http://", "").trim() },
                        label = { Text("Nama Domain") },
                        placeholder = { Text("misal: vip88-baru.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        label = { Text("Catatan Pendaftaran") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDomain.isNotBlank()) {
                            onAddRequest(newDomain, newCategory, newPath, newDns, newNotes)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Kirim Permohonan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun DomainRequestCard(
    domain: DomainRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    onSubmitGsc: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(domain.createdAt))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (domain.registrationStatus) {
                                    DomainRequest.REG_APPROVED -> StatusActive.copy(alpha = 0.15f)
                                    DomainRequest.REG_REJECTED -> StatusBlocked.copy(alpha = 0.15f)
                                    else -> StatusPending.copy(alpha = 0.2f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (domain.registrationStatus) {
                                DomainRequest.REG_APPROVED -> Icons.Default.CheckCircle
                                DomainRequest.REG_REJECTED -> Icons.Default.Cancel
                                else -> Icons.Default.HourglassTop
                            },
                            contentDescription = null,
                            tint = when (domain.registrationStatus) {
                                DomainRequest.REG_APPROVED -> StatusActive
                                DomainRequest.REG_REJECTED -> StatusBlocked
                                else -> Color(0xFFD97706)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = domain.domainName,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "${domain.category} • $dateStr",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (domain.registrationStatus) {
                        DomainRequest.REG_APPROVED -> StatusActive.copy(alpha = 0.15f)
                        DomainRequest.REG_REJECTED -> StatusBlocked.copy(alpha = 0.15f)
                        else -> StatusPending.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when (domain.registrationStatus) {
                            DomainRequest.REG_APPROVED -> "DISETUJUI"
                            DomainRequest.REG_REJECTED -> "DITOLAK"
                            else -> "PENDING APPROVAL"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (domain.registrationStatus) {
                            DomainRequest.REG_APPROVED -> StatusActive
                            DomainRequest.REG_REJECTED -> StatusBlocked
                            else -> Color(0xFFD97706)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (domain.notes.isNotBlank() || domain.rejectionReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (domain.notes.isNotBlank()) {
                    Text(
                        text = "📝 Catatan: ${domain.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (domain.rejectionReason.isNotBlank()) {
                    Text(
                        text = "⚠️ Alasan Penolakan: ${domain.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall.copy(color = StatusBlocked),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Workflow Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (domain.registrationStatus == DomainRequest.REG_PENDING) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusActive)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Setujui (Approve)", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusBlocked)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tolak", style = MaterialTheme.typography.labelSmall, color = StatusBlocked)
                    }
                } else if (domain.registrationStatus == DomainRequest.REG_APPROVED) {
                    Button(
                        onClick = onSubmitGsc,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kirim Indexing GSC", style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp), tint = Color.Gray)
                }
            }
        }
    }
}
