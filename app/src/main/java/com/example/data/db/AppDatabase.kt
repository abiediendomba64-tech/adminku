package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DomainRequest
import com.example.data.model.GscSubmissionLog
import com.example.data.model.MemberTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [DomainRequest::class, MemberTicket::class, GscSubmissionLog::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun ticketDao(): TicketDao
    abstract fun gscSubmissionDao(): GscSubmissionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backoffice_bot.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateInitialData(getInstance(context))
                            }
                        }
                    })
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val domainDao = db.domainDao()
            val ticketDao = db.ticketDao()

            // Seed sample domains
            domainDao.insertDomain(
                DomainRequest(
                    domainName = "panel-utama88.com",
                    category = "Landing Utama",
                    targetPath = "/backoffice",
                    status = DomainRequest.STATUS_ACTIVE,
                    dnsSettings = "Cloudflare Proxy (1.1.1.1)",
                    notes = "Domain utama backoffice server production"
                )
            )
            domainDao.insertDomain(
                DomainRequest(
                    domainName = "vip-memberlink.org",
                    category = "Mirror / Alternatif",
                    targetPath = "/backoffice/settings/marketing-tags",
                    status = DomainRequest.STATUS_PENDING,
                    dnsSettings = "A-Record 104.21.55.12",
                    notes = "Pengajuan domain alternatif untuk marketing tag & tracking"
                )
            )
            domainDao.insertDomain(
                DomainRequest(
                    domainName = "portal-aksescepat.net",
                    category = "Mobile App Redirect",
                    targetPath = "/backoffice",
                    status = DomainRequest.STATUS_INDEXED,
                    dnsSettings = "Cloudflare SSL Strict",
                    notes = "Sudah lolos verifikasi Google Search Console"
                )
            )

            // Seed sample tickets
            ticketDao.insertTicket(
                MemberTicket(
                    memberUsername = "alex_gaming77",
                    category = "Domain Terblokir / Nawala",
                    priority = MemberTicket.PRIORITY_HIGH,
                    domainAffected = "panel-utama88.com",
                    issueDescription = "Member lapor tidak bisa buka panel via Indihome / Telkomsel, muncul halaman Internet Positif.",
                    status = MemberTicket.STATUS_OPEN,
                    adminNotes = "Perlu dialihkan ke domain mirror vip-memberlink.org"
                )
            )
            ticketDao.insertTicket(
                MemberTicket(
                    memberUsername = "budi_santoso",
                    category = "Marketing Tag / Tracking",
                    priority = MemberTicket.PRIORITY_MEDIUM,
                    domainAffected = "vip-memberlink.org",
                    issueDescription = "Tracking marketing tags pixel belum terpasang di panel /backoffice/settings/marketing-tags.",
                    status = MemberTicket.STATUS_IN_PROGRESS,
                    adminNotes = "Sedang menunggu konfirmasi DNS domain alternatif"
                )
            )
            ticketDao.insertTicket(
                MemberTicket(
                    memberUsername = "citra_dewi88",
                    category = "Akses / Login",
                    priority = MemberTicket.PRIORITY_LOW,
                    domainAffected = "portal-aksescepat.net",
                    issueDescription = "Reset password member selesai dan sudah bisa login normal.",
                    status = MemberTicket.STATUS_RESOLVED,
                    adminNotes = "Tiket selesai diproses admin."
                )
            )

            // Seed sample GSC submission history logs across major search engines
            val gscDao = db.gscSubmissionDao()
            gscDao.insertLog(
                GscSubmissionLog(
                    url = "https://panel-utama88.com/backoffice",
                    domain = "panel-utama88.com",
                    searchEngine = "Google Search Console",
                    httpStatus = 200,
                    verificationTagFound = true,
                    sitemapDetected = true,
                    indexingStatus = GscSubmissionLog.STATUS_INDEXED,
                    diagnosticMessage = "URL terverifikasi dan telah berhasil masuk indeks Google Search.",
                    gscTrackingId = "GSC-REQ-882190",
                    submittedAt = System.currentTimeMillis() - 7200_000L
                )
            )
            gscDao.insertLog(
                GscSubmissionLog(
                    url = "https://panel-utama88.com/backoffice/settings/marketing-tags",
                    domain = "panel-utama88.com",
                    searchEngine = "Google Search Console",
                    httpStatus = 200,
                    verificationTagFound = true,
                    sitemapDetected = true,
                    indexingStatus = GscSubmissionLog.STATUS_SUBMITTED,
                    diagnosticMessage = "Permintaan index berhasil diajukan ke Googlebot. Antrean perayapan normal.",
                    gscTrackingId = "GSC-REQ-883412",
                    submittedAt = System.currentTimeMillis() - 3600_000L
                )
            )
            gscDao.insertLog(
                GscSubmissionLog(
                    url = "https://vip-memberlink.org/sitemap.xml",
                    domain = "vip-memberlink.org",
                    searchEngine = "Bing Webmaster Tools",
                    httpStatus = 200,
                    verificationTagFound = false,
                    sitemapDetected = true,
                    indexingStatus = GscSubmissionLog.STATUS_PENDING_CRAWL,
                    diagnosticMessage = "Sitemap diterima via Bing Webmaster API, menunggu bot perayap.",
                    gscTrackingId = "BNG-REQ-441209",
                    submittedAt = System.currentTimeMillis() - 1800_000L
                )
            )
            gscDao.insertLog(
                GscSubmissionLog(
                    url = "https://portal-aksescepat.net",
                    domain = "portal-aksescepat.net",
                    searchEngine = "Yandex Webmaster",
                    httpStatus = 200,
                    verificationTagFound = true,
                    sitemapDetected = false,
                    indexingStatus = GscSubmissionLog.STATUS_VERIFIED_OWNERSHIP,
                    diagnosticMessage = "Meta-tag Yandex terverifikasi. Pengajuan sitemap disarankan.",
                    gscTrackingId = "YND-REQ-992011",
                    submittedAt = System.currentTimeMillis() - 600_000L
                )
            )
        }
    }
}
