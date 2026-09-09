# Database Backup and Recovery

PostgreSQL is the system of record. The repository provides a logical backup and restore procedure for the maintained
Docker Compose runtime. A deployment platform must provide equivalent scheduled backups, encrypted storage, retention,
access control, and restore testing before it carries production data.

## Recovery objectives

Recovery objectives depend on the deployment and have not been approved. The current procedure creates point-in-time
logical snapshots on demand. It does not provide continuous archiving or point-in-time recovery between snapshots.
Choose a backup frequency and retention period only after the business owner defines acceptable data loss and recovery
time.

## Create a backup

Start the stack and provide a new output path:

```bash
docker compose up --detach --wait
scripts/backup-database.sh backups/smart-parking-$(date -u +%Y%m%dT%H%M%SZ).dump
```

The command writes a PostgreSQL custom-format archive and a sibling SHA-256 checksum. It refuses to overwrite existing
files, validates the archive before publishing it, and restricts newly created files through the process umask. Move
both files to controlled storage after the command completes. A local file is not a durable backup.

Backups contain vehicle identifiers, session history, identity-provider subjects, and operational records. Encrypt
them at rest and in transit. Limit restore and download permission to authorized administrators. Do not commit backup
files or checksums to the repository.

## Restore the local database

The restore replaces the complete `smart_parking` database. Verify the target environment and backup path before
setting the confirmation value:

```bash
SMART_PARKING_RESTORE_CONFIRM=restore-smart-parking \
  scripts/restore-database.sh backups/smart-parking-20260909T230000Z.dump
```

The command verifies the checksum and archive, stops the application services, recreates the database, restores the
archive in one transaction, and waits for the backend and dashboard health checks. If the restore fails after services
stop, it makes a best-effort attempt to restart them and returns a nonzero status. Preserve the failed command output
and PostgreSQL logs for diagnosis.

The script is deliberately fixed to the local Compose service, database, and role. A production recovery procedure
must use platform-native credentials and controls rather than modifying this guard.

## Recovery drill

Run the maintained destructive drill only against disposable local data:

```bash
scripts/verify-database-recovery.sh
```

The drill confirms the 7,200-space fixture, creates and validates a backup, adds a post-backup marker, restores the
database, proves that the marker disappeared, verifies the facility count, and checks backend readiness. Runtime CI
runs this drill for changes to the application stack or recovery scripts.

After any real recovery, verify at minimum:

- database, application, and dashboard health
- the expected Flyway schema version
- facility and active-session counts against the recovery record
- recent reservations, receipts, and audit history
- entry and exit using a controlled test vehicle

Record the backup timestamp, incident or drill identifier, restored environment, start and completion times, operator,
verification results, and follow-up actions. Never place credentials or personal data in the recovery record.
