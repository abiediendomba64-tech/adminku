#!/usr/bin/env python3
"""SQLite migration smoke test using Python's stdlib; no remote D1 writes."""
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
BASELINE = (ROOT / "migrations" / "0000_legacy_baseline.sql").read_text()
MIGRATION = (ROOT / "migrations" / "0001_backoffice_api.sql").read_text()

con = sqlite3.connect(":memory:")
con.execute("PRAGMA foreign_keys = ON")
con.executescript(BASELINE)

# Existing production-like row must survive rerunning the baseline and additive migration.
con.execute("""INSERT INTO member_tickets
(ticketCode, memberUsername, category, priority, domainAffected, issueDescription, status,
 reportedVia, createdAt, lastUpdated, adminNotes, telegramUserId, telegramChatId)
VALUES ('TKT-LEGACY-1234', '@legacy', 'Support', 'TINGGI', 'example.com', 'Existing issue',
'OPEN', 'TELEGRAM_BOT', 1700000000000, 1700000000000, '', '123456789', '123456789')""")
con.executescript(BASELINE)
con.executescript(MIGRATION)
con.executescript(MIGRATION)

row = con.execute("SELECT ticketCode, issueDescription, createdAt FROM member_tickets WHERE ticketCode='TKT-LEGACY-1234'").fetchone()
assert row == ('TKT-LEGACY-1234', 'Existing issue', 1700000000000), row

# The baseline is exactly the existing 38-column schema.
legacy_columns = sum(len(con.execute(f"PRAGMA table_info({table})").fetchall()) for table in
                     ("domain_requests", "member_tickets", "gsc_submission_logs"))
assert legacy_columns == 38, legacy_columns

# Runtime-allowlisted admins cannot be granted by a D1 role row.
try:
    con.execute("""INSERT INTO users (telegramUserId, role, createdAt, updatedAt)
                   VALUES ('999999999', 'ADMIN', 1, 1)""")
    raise AssertionError("users.role accepted ADMIN")
except sqlite3.IntegrityError:
    pass

# A ticket history row is linked to the existing ticket; delete/update are blocked.
con.execute("""INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, createdAt)
               VALUES ('TKT-LEGACY-1234', NULL, 'OPEN', 'created', 1700000000001)""")
for sql in (
    "UPDATE ticket_history SET note='tampered' WHERE ticketCode='TKT-LEGACY-1234'",
    "DELETE FROM ticket_history WHERE ticketCode='TKT-LEGACY-1234'",
):
    try:
        con.execute(sql)
        raise AssertionError(f"append-only guard failed: {sql}")
    except sqlite3.IntegrityError:
        pass

con.execute("""INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action,
               resourceType, resourceId, detailsJson, createdAt)
               VALUES ('123456789', '@admin', 'ADMIN', 'CREATE', 'ticket', 'TKT-LEGACY-1234', '{}', 1)""")
for sql in (
    "UPDATE audit_logs SET detailsJson='{}' WHERE resourceId='TKT-LEGACY-1234'",
    "DELETE FROM audit_logs WHERE resourceId='TKT-LEGACY-1234'",
):
    try:
        con.execute(sql)
        raise AssertionError(f"audit append-only guard failed: {sql}")
    except sqlite3.IntegrityError:
        pass

# Foreign key RESTRICT protects history from ticket deletion.
try:
    con.execute("DELETE FROM member_tickets WHERE ticketCode='TKT-LEGACY-1234'")
    raise AssertionError("ticket deletion bypassed ticket_history FK")
except sqlite3.IntegrityError:
    pass

# Simulate D1 batch's ordered, same-connection changes() gating.
con.execute("INSERT INTO member_tickets (ticketCode, memberUsername, category, issueDescription, createdAt, lastUpdated) VALUES ('TKT-BATCH-1', 'u', 'x', 'issue', 1, 1)")
con.commit()
con.execute("BEGIN")
con.execute("UPDATE member_tickets SET status='DIPROSES' WHERE ticketCode='TKT-BATCH-1' AND status='OPEN'")
con.execute("""INSERT INTO audit_logs (actorTelegramUserId, actorUsername, actorRole, action, resourceType, resourceId, detailsJson, createdAt)
               SELECT '1','u','ADMIN','UPDATE','ticket','TKT-BATCH-1','{}',2 WHERE changes() > 0""")
con.execute("""INSERT INTO ticket_history (ticketCode, oldStatus, newStatus, note, createdAt)
               SELECT 'TKT-BATCH-1','OPEN','DIPROSES','changed',2 WHERE changes() > 0 AND 'OPEN' <> 'DIPROSES'""")
con.commit()
assert con.execute("SELECT COUNT(*) FROM ticket_history WHERE ticketCode='TKT-BATCH-1'").fetchone()[0] == 1
assert con.execute("SELECT COUNT(*) FROM audit_logs WHERE resourceId='TKT-BATCH-1'").fetchone()[0] == 1

print("PASS: baseline is idempotent; legacy data preserved; 38 legacy columns; additive migration applies twice;")
print("PASS: ADMIN role blocked at DB layer; FK RESTRICT; ticket_history and audit_logs append-only;")
print("PASS: sequential batch changes() gating records ticket status update atomically.")
