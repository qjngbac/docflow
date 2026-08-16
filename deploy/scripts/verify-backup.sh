#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_DIR="${1:-}"

fail() {
  printf 'DocFlow backup verification failed: %s\n' "$*" >&2
  exit 1
}

[[ -n "${BACKUP_DIR}" && -d "${BACKUP_DIR}" ]] || fail "usage: verify-backup.sh <backup-directory>"
[[ -f "${BACKUP_DIR}/SHA256SUMS" ]] || fail "SHA256SUMS is missing"
[[ -f "${BACKUP_DIR}/mysql.sql.gz" ]] || fail "mysql.sql.gz is missing"

(
  cd "${BACKUP_DIR}"
  sha256sum -c SHA256SUMS
  gzip -t mysql.sql.gz
  if [[ -f uploads.tar.gz ]]; then
    if tar -tzf uploads.tar.gz | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
      fail "uploads archive contains an unsafe path"
    fi
    tar -tzf uploads.tar.gz >/dev/null
  fi
)

printf 'DocFlow backup is valid: %s\n' "${BACKUP_DIR}"
