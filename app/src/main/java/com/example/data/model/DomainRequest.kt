package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_requests")
data class DomainRequest(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domainName: String,
    val category: String, // "Landing Utama", "Mirror / Alternatif", "Mobile App Redirect", "Marketing Tag"
    val targetPath: String = "/backoffice", // "/backoffice" or "/backoffice/settings/marketing-tags"
    val status: String = STATUS_PENDING, // PENDING_DNS, ACTIVE, INDEXED_GSC, BLOCKED_NAWALA
    val dnsSettings: String = "Cloudflare Proxy",
    val notes: String = "",
    val gscIndexingStatus: String = "SUBMITTED", // "INDEXED", "SUBMITTED", "PENDING_CRAWL", "NOT_INDEXED"
    val lastReindexedAt: Long? = null,
    val verificationTag: String = "",
    val registrationStatus: String = REG_PENDING, // PENDING, APPROVED, REJECTED
    val rejectionReason: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING_DNS"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_INDEXED = "INDEXED_GSC"
        const val STATUS_BLOCKED = "BLOCKED_NAWALA"

        const val REG_PENDING = "PENDING"
        const val REG_APPROVED = "APPROVED"
        const val REG_REJECTED = "REJECTED"

        const val GSC_INDEXED = "INDEXED"
        const val GSC_SUBMITTED = "SUBMITTED"
        const val GSC_PENDING = "PENDING_CRAWL"
        const val GSC_NOT_INDEXED = "NOT_INDEXED"
    }
}
