#!/bin/bash
# Epiphaneia database restore script
# Usage: ./restore-db.sh <backup_file.sql.gz>

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup_file.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "Error: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

DB_USER="${DB_USER:-epiphaneia}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-epiphaneia}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

echo "WARNING: This will overwrite database '${DB_NAME}'."
echo "Restoring from: ${BACKUP_FILE}"
echo ""

read -p "Are you sure you want to continue? (yes/no): " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

echo "Restoring database ..."

gunzip -c "${BACKUP_FILE}" | PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}"

echo "Restore complete."
