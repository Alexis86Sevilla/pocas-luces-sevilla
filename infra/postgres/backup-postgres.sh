#!/usr/bin/env bash
#
# Daily backup of the sevillasinluz PostgreSQL database.
#
# Auth: relies on either the postgres peer auth (run as the `postgres` OS user)
# or a ~/.pgpass entry for the DB user (format: hostname:port:database:user:password).
# Does NOT take a password on the command line or in this script.
#
# Install:
#   sudo install -m 0750 -o postgres -g postgres infra/postgres/backup-postgres.sh /usr/local/sbin/backup-postgres.sh
#   sudo mkdir -p /var/backups/sevillasinluz && sudo chown postgres:postgres /var/backups/sevillasinluz
#
# Run manually / verify:
#   sudo -u postgres /usr/local/sbin/backup-postgres.sh
#   ls -la /var/backups/sevillasinluz
#
# Rollback:
#   sudo rm /usr/local/sbin/backup-postgres.sh
#   (existing backup files are untouched; delete them manually if desired)

set -euo pipefail

DB_NAME="${DB_NAME:-sevillasinluz}"
DB_USER="${DB_USER:-sevillasinluz_user}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/sevillasinluz}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

umask 077

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "backup-postgres: pg_dump not found in PATH" >&2
  exit 1
fi

mkdir -p "${BACKUP_DIR}"

timestamp="$(date +%Y%m%d-%H%M%S)"
dest="${BACKUP_DIR}/${DB_NAME}-${timestamp}.dump"
tmp_dest="${dest}.in-progress"

echo "backup-postgres: dumping ${DB_NAME}@${DB_HOST}:${DB_PORT} to ${dest}"

if ! pg_dump -Fc \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --username="${DB_USER}" \
  --no-password \
  --file="${tmp_dest}" \
  "${DB_NAME}"; then
  echo "backup-postgres: pg_dump failed" >&2
  rm -f "${tmp_dest}"
  exit 1
fi

mv "${tmp_dest}" "${dest}"
echo "backup-postgres: wrote $(du -h "${dest}" | cut -f1) to ${dest}"

echo "backup-postgres: pruning dumps older than ${RETENTION_DAYS} days"
find "${BACKUP_DIR}" -maxdepth 1 -name "${DB_NAME}-*.dump" -mtime "+${RETENTION_DAYS}" -print -delete

echo "backup-postgres: done"
