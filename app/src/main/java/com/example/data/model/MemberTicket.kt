package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Random

@Entity(tableName = "member_tickets")
data class MemberTicket(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ticketCode: String = generateTicketCode(),
    val memberUsername: String,
    val category: String, // "Akses / Login", "Domain Terblokir / Nawala", "Deposit / WD", "Marketing Tag / Tracking", "Lainnya"
    val priority: String = PRIORITY_MEDIUM, // TINGGI, SEDANG, RENDAH
    val domainAffected: String = "",
    val issueDescription: String,
    val status: String = STATUS_OPEN, // OPEN, IN_PROGRESS, RESOLVED
    val reportedVia: String = "TELEGRAM_BOT", // "TELEGRAM_BOT", "WEB_APP", "MANUAL"
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val adminNotes: String = ""
) {
    companion object {
        const val PRIORITY_HIGH = "TINGGI"
        const val PRIORITY_MEDIUM = "SEDANG"
        const val PRIORITY_LOW = "RENDAH"

        const val STATUS_OPEN = "OPEN"
        const val STATUS_IN_PROGRESS = "DIPROSES"
        const val STATUS_RESOLVED = "SELESAI"
        const val STATUS_REJECTED = "DITOLAK"

        fun generateTicketCode(): String {
            val randomNum = 1000 + Random().nextInt(9000)
            return "TKT-$randomNum"
        }
    }
}
