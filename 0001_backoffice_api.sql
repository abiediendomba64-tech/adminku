-- Additive D1 migration for the Adminku backoffice REST API.
-- The three legacy tables remain untouched: domain_requests, member_tickets,
-- and gsc_submission_logs keep their existing camelCase columns and epoch INTEGER timestamps.
-- Apply locally first; apply remotely only after backup and explicit approval.

CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  telegramUserId TEXT NOT NULL UNIQUE,
  username TEXT NOT NULL DEFAULT '',
  firstName TEXT NOT NULL DEFAULT '',
  lastName TEXT NOT NULL DEFAULT '',
  role TEXT NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('STAFF','VIEWER','MEMBER')),
  isActive INTEGER NOT NULL DEFAULT 1 CHECK (isActive IN (0,1)),
  notes TEXT NOT NULL DEFAULT '',
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL,
  createdBy TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS marketing_tags (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  tagType TEXT NOT NULL,
  tagId TEXT NOT NULL,
  targetDomain TEXT NOT NULL DEFAULT '*',
  targetPath TEXT NOT NULL DEFAULT '/',
  placement TEXT NOT NULL DEFAULT 'HEAD' CHECK (placement IN ('HEAD','BODY_START','BODY_END')),
  isActive INTEGER NOT NULL DEFAULT 1 CHECK (isActive IN (0,1)),
  notes TEXT NOT NULL DEFAULT '',
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL,
  createdBy TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ticket_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ticketCode TEXT NOT NULL,
  oldStatus TEXT,
  newStatus TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  changedByTelegramUserId TEXT NOT NULL DEFAULT '',
  changedByUsername TEXT NOT NULL DEFAULT '',
  source TEXT NOT NULL DEFAULT 'WEB_APP',
  createdAt INTEGER NOT NULL,
  FOREIGN KEY (ticketCode) REFERENCES member_tickets(ticketCode) ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actorTelegramUserId TEXT NOT NULL,
  actorUsername TEXT NOT NULL DEFAULT '',
  actorRole TEXT NOT NULL,
  action TEXT NOT NULL,
  resourceType TEXT NOT NULL,
  resourceId TEXT NOT NULL DEFAULT '',
  detailsJson TEXT NOT NULL DEFAULT '{}',
  createdAt INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_role_active ON users(role, isActive);
CREATE INDEX IF NOT EXISTS idx_marketing_tags_active ON marketing_tags(isActive, createdAt);
CREATE INDEX IF NOT EXISTS idx_marketing_tags_domain ON marketing_tags(targetDomain);
CREATE INDEX IF NOT EXISTS idx_ticket_history_code_created ON ticket_history(ticketCode, createdAt DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(createdAt DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resourceType, resourceId, createdAt DESC);

-- Audit history is append-only. Corrections must be represented by new events.
CREATE TRIGGER IF NOT EXISTS trg_audit_logs_no_update
BEFORE UPDATE ON audit_logs
BEGIN
  SELECT RAISE(ABORT, 'audit_logs is append-only');
END;

CREATE TRIGGER IF NOT EXISTS trg_audit_logs_no_delete
BEFORE DELETE ON audit_logs
BEGIN
  SELECT RAISE(ABORT, 'audit_logs is append-only');
END;

CREATE TRIGGER IF NOT EXISTS trg_ticket_history_no_update
BEFORE UPDATE ON ticket_history
BEGIN
  SELECT RAISE(ABORT, 'ticket_history is append-only');
END;

CREATE TRIGGER IF NOT EXISTS trg_ticket_history_no_delete
BEFORE DELETE ON ticket_history
BEGIN
  SELECT RAISE(ABORT, 'ticket_history is append-only');
END;
