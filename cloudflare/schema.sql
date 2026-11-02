-- Canonical schema for Cloudflare D1 database: adminku-db
-- Apply to a new database with: npx wrangler d1 execute adminku-db --remote --file=./schema.sql
-- The current production database already has these tables and indexes.

CREATE TABLE IF NOT EXISTS domain_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domainName TEXT NOT NULL,
    category TEXT NOT NULL,
    targetPath TEXT NOT NULL DEFAULT '/backoffice',
    status TEXT NOT NULL DEFAULT 'PENDING_DNS',
    dnsSettings TEXT NOT NULL DEFAULT 'Cloudflare Proxy',
    notes TEXT NOT NULL DEFAULT '',
    gscIndexingStatus TEXT NOT NULL DEFAULT 'SUBMITTED',
    lastReindexedAt INTEGER,
    verificationTag TEXT NOT NULL DEFAULT '',
    registrationStatus TEXT NOT NULL DEFAULT 'PENDING',
    rejectionReason TEXT NOT NULL DEFAULT '',
    createdAt INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS member_tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticketCode TEXT NOT NULL UNIQUE,
    memberUsername TEXT NOT NULL,
    category TEXT NOT NULL,
    priority TEXT NOT NULL DEFAULT 'SEDANG',
    domainAffected TEXT NOT NULL DEFAULT '',
    issueDescription TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    reportedVia TEXT NOT NULL DEFAULT 'TELEGRAM_BOT',
    createdAt INTEGER NOT NULL,
    lastUpdated INTEGER NOT NULL,
    adminNotes TEXT NOT NULL DEFAULT '',
    telegramUserId TEXT NOT NULL DEFAULT '',
    telegramChatId TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS gsc_submission_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT NOT NULL,
    domain TEXT NOT NULL,
    searchEngine TEXT NOT NULL DEFAULT 'Google',
    httpStatus INTEGER NOT NULL DEFAULT 200,
    verificationTagFound INTEGER NOT NULL DEFAULT 0,
    sitemapDetected INTEGER NOT NULL DEFAULT 0,
    indexingStatus TEXT NOT NULL DEFAULT 'SUBMITTED',
    diagnosticMessage TEXT NOT NULL DEFAULT '',
    gscTrackingId TEXT NOT NULL UNIQUE,
    submittedAt INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_domains_name ON domain_requests(domainName);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON member_tickets(status);
CREATE INDEX IF NOT EXISTS idx_gsc_domain ON gsc_submission_logs(domain);
