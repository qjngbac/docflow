#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

BACKUP_DIR="${1:-}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-/etc/docflow/mysql-backup.cnf}"
DB_NAME="${DB_NAME:-docflow}"
UPLOAD_DIR="${UPLOAD_DIR:-/srv/docflow/uploads}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

fail() {
  printf 'DocFlow restore failed: %s\n' "$*" >&2
  exit 1
}

[[ "${RESTORE_CONFIRM:-}" == "RESTORE_DOCFLOW" ]] || fail "set RESTORE_CONFIRM=RESTORE_DOCFLOW to confirm"
[[ -n "${BACKUP_DIR}" && -d "${BACKUP_DIR}" ]] || fail "usage: restore.sh <backup-directory>"
[[ -r "${MYSQL_DEFAULTS_FILE}" ]] || fail "cannot read ${MYSQL_DEFAULTS_FILE}"
[[ -n "${UPLOAD_DIR}" && "${UPLOAD_DIR}" != "/" ]] || fail "unsafe UPLOAD_DIR"

"${SCRIPT_DIR}/verify-backup.sh" "${BACKUP_DIR}"

gzip -dc "${BACKUP_DIR}/mysql.sql.gz" | mysql \
  --defaults-extra-file="${MYSQL_DEFAULTS_FILE}" \
  --default-character-set=utf8mb4 \
  "${DB_NAME}"

if [[ "${RESTORE_UPLOADS:-true}" == "true" && -f "${BACKUP_DIR}/uploads.tar.gz" ]]; then
  upload_parent="$(dirname "${UPLOAD_DIR}")"
  previous_dir="${UPLOAD_DIR}.pre-restore.$(date -u +%Y%m%dT%H%M%SZ)"
  staging_dir="${UPLOAD_DIR}.restore.$$"
  mkdir -p "${upload_parent}"
  mkdir -p "${staging_dir}"
  tar -C "${staging_dir}" -xzf "${BACKUP_DIR}/uploads.tar.gz"
  if [[ -e "${UPLOAD_DIR}" ]]; then mv -- "${UPLOAD_DIR}" "${previous_dir}"; fi
  mv -- "${staging_dir}" "${UPLOAD_DIR}"
  [[ -d "${UPLOAD_DIR}" ]] || fail "uploads were not restored"
fi

printf 'DocFlow restore completed. Start services and run the smoke tests before reopening traffic.\n'
