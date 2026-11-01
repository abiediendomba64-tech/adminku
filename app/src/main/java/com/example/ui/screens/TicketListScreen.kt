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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.data.model.MemberTicket
import com.example.data.network.TelegramApiService
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusPending
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilledTonalButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketListScreen(
    tickets: List<MemberTicket>,
    onAddTicket: (member: String, cat: String, prio: String, domain: String, desc: String, notes: String) -> Unit,
    onUpdateStatus: (MemberTicket, String) -> Unit,
    onDeleteTicket: (MemberTicket) -> Unit,
    onSelectTicket: ((MemberTicket) -> Unit)? = null
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTickets = tickets.filter {
        val matchesFilter = when (selectedStatusFilter) {
            "OPEN" -> it.status == MemberTicket.STATUS_OPEN
            "IN_PROGRESS" -> it.status == MemberTicket.STATUS_IN_PROGRESS
            "RESOLVED" -> it.status == MemberTicket.STATUS_RESOLVED
            else -> true
        }
        val matchesSearch = it.memberUsername.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.domainAffected.contains(searchQuery, ignoreCase = true) ||
                it.issueDescription.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesSearch
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Tiket Kendala")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Search & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari username member, kendala, domain...") },
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
                        selected = selectedStatusFilter == "ALL",
                        onClick = { selectedStatusFilter = "ALL" },
                        label = { Text("Semua (${tickets.size})") }
                    )
                    FilterChip(
                        selected = selectedStatusFilter == "OPEN",
                        onClick = { selectedStatusFilter = "OPEN" },
                        label = { Text("⏳ Baru (${tickets.count { it.status == MemberTicket.STATUS_OPEN }})") }
                    )
                    FilterChip(
                        selected = selectedStatusFilter == "IN_PROGRESS",
                        onClick = { selectedStatusFilter = "IN_PROGRESS" },
                        label = { Text("⚙️ Proses (${tickets.count { it.status == MemberTicket.STATUS_IN_PROGRESS }})") }
                    )
                    FilterChip(
                        selected = selectedStatusFilter == "RESOLVED",
                        onClick = { selectedStatusFilter = "RESOLVED" },
                        label = { Text("✅ Selesai (${tickets.count { it.status == MemberTicket.STATUS_RESOLVED }})") }
                    )
                }
            }

            // Ticket list
            if (filteredTickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Tiket tidak ditemukan" else "Belum ada tiket kendala",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gunakan tombol + untuk mendata kendala member baru.",
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
                    items(filteredTickets, key = { it.id }) { ticket ->
                        TicketCardItem(
                            ticket = ticket,
                            onUpdateStatus = { newStatus -> onUpdateStatus(ticket, newStatus) },
                            onDelete = { onDeleteTicket(ticket) },
                            onShareTicket = {
                                val text = buildSingleTicketReport(ticket)
                                TelegramApiService.openTelegramShareIntent(context, text)
                            },
                            onSelectTicket = onSelectTicket?.let { callback -> { callback(ticket) } }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTicketDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { mem, cat, prio, dom, desc, notes ->
                onAddTicket(mem, cat, prio, dom, desc, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun TicketCardItem(
    ticket: MemberTicket,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onShareTicket: () -> Unit,
    onSelectTicket: (() -> Unit)? = null
) {
    var statusMenuOpen by remember { mutableStateOf(false) }

    val priorityColor = when (ticket.priority) {
        MemberTicket.PRIORITY_HIGH -> StatusBlocked
        MemberTicket.PRIORITY_MEDIUM -> StatusPending
        else -> StatusActive
    }

    val (statusColor, statusLabel) = when (ticket.status) {
        MemberTicket.STATUS_OPEN -> StatusPending to "⏳ BARU"
        MemberTicket.STATUS_IN_PROGRESS -> MaterialTheme.colorScheme.primary to "⚙️ DIPROSES"
        else -> StatusActive to "✅ SELESAI"
    }

    val dateFormatted = remember(ticket.createdAt) {
        val sdf = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        sdf.format(Date(ticket.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Member Username & Priority & Status
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
                            .background(priorityColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = priorityColor)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "#${ticket.ticketCode}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = ticket.memberUsername,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "${ticket.category} • $dateFormatted",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip with dropdown
                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { statusMenuOpen = true },
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = statusMenuOpen,
                        onDismissRequest = { statusMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("⏳ Set Baru (Open)") },
                            onClick = {
                                onUpdateStatus(MemberTicket.STATUS_OPEN)
                                statusMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⚙️ Set Sedang Diproses") },
                            onClick = {
                                onUpdateStatus(MemberTicket.STATUS_IN_PROGRESS)
                                statusMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("✅ Set Selesai (Resolved)") },
                            onClick = {
                                onUpdateStatus(MemberTicket.STATUS_RESOLVED)
                                statusMenuOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (ticket.domainAffected.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "Domain: ${ticket.domainAffected}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = ticket.issueDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (ticket.adminNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Catatan Admin: ${ticket.adminNotes}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSelectTicket != null) {
                        FilledTonalButton(
                            onClick = onSelectTicket,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Detail & AI", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    OutlinedButton(
                        onClick = onShareTicket,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Telegram", style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddTicketDialog(
    onDismiss: () -> Unit,
    onConfirm: (member: String, cat: String, prio: String, domain: String, desc: String, notes: String) -> Unit
) {
    var memberUsername by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Domain Terblokir / Nawala") }
    var priority by remember { mutableStateOf(MemberTicket.PRIORITY_HIGH) }
    var domainAffected by remember { mutableStateOf("") }
    var issueDescription by remember { mutableStateOf("") }
    var adminNotes by remember { mutableStateOf("") }

    val categories = listOf(
        "Domain Terblokir / Nawala",
        "Akses / Gagal Login",
        "Deposit / Withdraw",
        "Marketing Tag / Tracking",
        "Lainnya"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎫 Input Tiket Kendala Member", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = memberUsername,
                    onValueChange = { memberUsername = it },
                    label = { Text("Username / Member ID") },
                    placeholder = { Text("misal: alex_99") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Kategori Kendala:", style = MaterialTheme.typography.labelMedium)
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
                Text("Tingkat Prioritas:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        MemberTicket.PRIORITY_HIGH to "🔴 Tinggi",
                        MemberTicket.PRIORITY_MEDIUM to "🟡 Sedang",
                        MemberTicket.PRIORITY_LOW to "🟢 Rendah"
                    ).forEach { (prio, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { priority = prio }
                        ) {
                            RadioButton(selected = priority == prio, onClick = { priority = prio })
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = domainAffected,
                    onValueChange = { domainAffected = it },
                    label = { Text("Domain Terdampak (Opsional)") },
                    placeholder = { Text("panel-utama88.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = issueDescription,
                    onValueChange = { issueDescription = it },
                    label = { Text("Rincian Kendala Member") },
                    placeholder = { Text("Jelaskan pesan error atau kendala yang dialami member...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = adminNotes,
                    onValueChange = { adminNotes = it },
                    label = { Text("Catatan Internal Admin") },
                    placeholder = { Text("Tindakan awal / saran domain alternatif...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (memberUsername.isNotBlank() && issueDescription.isNotBlank()) {
                        onConfirm(memberUsername, category, priority, domainAffected, issueDescription, adminNotes)
                    }
                },
                enabled = memberUsername.isNotBlank() && issueDescription.isNotBlank()
            ) {
                Text("Simpan Tiket")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

private fun buildSingleTicketReport(ticket: MemberTicket): String {
    return """
        🚨 <b>TIKET KENDALA MEMBER</b>
        🆔 <b>Kode:</b> <code>#${ticket.ticketCode}</code>
        👤 <b>Member:</b> ${ticket.memberUsername}
        🏷️ <b>Kategori:</b> ${ticket.category}
        ⚡ <b>Prioritas:</b> ${ticket.priority}
        🌐 <b>Domain:</b> ${if (ticket.domainAffected.isNotEmpty()) ticket.domainAffected else "-"}
        📝 <b>Deskripsi:</b> ${ticket.issueDescription}
        📌 <b>Status:</b> ${ticket.status}
        ${if (ticket.adminNotes.isNotEmpty()) "💬 <b>Catatan:</b> " + ticket.adminNotes else ""}
    """.trimIndent()
}
