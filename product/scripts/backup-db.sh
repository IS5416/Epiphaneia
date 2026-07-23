#!/bin/bash
# Epiphaneia database backup script
# Usage: ./backup-db.sh [backup_dir]

set -euo pipefail

BACKUP_DIR="${1:-./backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/epiphaneia_backup_${TIMESTAMP}.sql.gz"

DB_USER="${DB_USER:-epiphaneia}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-epiphaneia}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

mkdir -p "${BACKUP_DIR}"

echo "Backing up database '${DB_NAME}' to ${BACKUP_FILE} ..."

PGPASSWORD="${DB_PASSWORD}" pg_dump \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    --no-owner \
    --no-acl \
    | gzip > "${BACKUP_FILE}"

echo "Backup complete: ${BACKUP_FILE}"
ls -lh "${BACKUP_FILE}"
