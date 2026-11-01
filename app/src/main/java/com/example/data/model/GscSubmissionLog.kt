package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Random

@Entity(tableName = "gsc_submission_logs")
data class GscSubmissionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val domain: String,
    val searchEngine: String = "Google", // Google, Bing, Baidu, Yandex, Naver, Seznam, Cốc Cốc, Brave, Mojeek, Perplexity, Multi-Engine
    val httpStatus: Int = 200,
    val verificationTagFound: Boolean = false,
    val sitemapDetected: Boolean = false,
    val indexingStatus: String = STATUS_SUBMITTED, // "INDEXED", "SUBMITTED", "PENDING_CRAWL", "VERIFIED_OWNERSHIP", "NOT_VERIFIED", "ERROR"
    val diagnosticMessage: String = "",
    val gscTrackingId: String = generateTrackingId(),
    val submittedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_INDEXED = "INDEXED"
        const val STATUS_SUBMITTED = "SUBMITTED"
        const val STATUS_PENDING_CRAWL = "PENDING_CRAWL"
        const val STATUS_VERIFIED_OWNERSHIP = "VERIFIED_OWNERSHIP"
        const val STATUS_NOT_VERIFIED = "NOT_VERIFIED"
        const val STATUS_ERROR = "ERROR"

        fun generateTrackingId(): String {
            val random = 100000 + Random().nextInt(900000)
            return "GSC-REQ-$random"
        }
    }
}
