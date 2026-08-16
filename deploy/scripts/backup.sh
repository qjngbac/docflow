#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/docflow}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-/etc/docflow/mysql-backup.cnf}"
DB_NAME="${DB_NAME:-docflow}"
UPLOAD_DIR="${UPLOAD_DIR:-/srv/docflow/uploads}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
NODE_EXPORTER_TEXTFILE_DIR="${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node_exporter/textfile_collector}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FINAL_DIR="${BACKUP_ROOT}/${TIMESTAMP}"
WORK_DIR="${BACKUP_ROOT}/.${TIMESTAMP}.partial"
LOCK_FILE="${BACKUP_ROOT}/.backup.lock"

fail() {
  printf 'DocFlow backup failed: %s\n' "$*" >&2
  exit 1
}

write_last_run_metric() {
  local success="$1"
  [[ -d "${NODE_EXPORTER_TEXTFILE_DIR}" && -w "${NODE_EXPORTER_TEXTFILE_DIR}" ]] || return 0
  printf 'docflow_backup_last_run_success %s\n' "${success}" \
    >"${NODE_EXPORTER_TEXTFILE_DIR}/docflow_backup_last_run.prom.tmp"
  mv -- "${NODE_EXPORTER_TEXTFILE_DIR}/docflow_backup_last_run.prom.tmp" \
    "${NODE_EXPORTER_TEXTFILE_DIR}/docflow_backup_last_run.prom"
}

for command_name in mysqldump gzip tar sha256sum flock; do
  command -v "${command_name}" >/dev/null 2>&1 || fail "missing command: ${command_name}"
done

[[ -n "${BACKUP_ROOT}" && "${BACKUP_ROOT}" != "/" ]] || fail "unsafe BACKUP_ROOT"
[[ -r "${MYSQL_DEFAULTS_FILE}" ]] || fail "cannot read ${MYSQL_DEFAULTS_FILE}"
[[ "${RETENTION_DAYS}" =~ ^[0-9]+$ ]] || fail "RETENTION_DAYS must be a non-negative integer"

mkdir -p "${BACKUP_ROOT}"
exec 9>"${LOCK_FILE}"
flock -n 9 || fail "another backup is already running"

cleanup() {
  status=$?
  rm -rf -- "${WORK_DIR}"
  if [[ ${status} -ne 0 ]]; then write_last_run_metric 0; fi
  return "${status}"
}
trap cleanup EXIT
mkdir -p "${WORK_DIR}"

mysqldump \
  --defaults-extra-file="${MYSQL_DEFAULTS_FILE}" \
  --single-transaction \
  --quick \
  --routines \
  --events \
  --triggers \
  --hex-blob \
  --no-tablespaces \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  "${DB_NAME}" | gzip -9 >"${WORK_DIR}/mysql.sql.gz"

if [[ -d "${UPLOAD_DIR}" ]]; then
  tar -C "${UPLOAD_DIR}" -czf "${WORK_DIR}/uploads.tar.gz" .
else
  printf 'Upload directory did not exist at backup time: %s\n' "${UPLOAD_DIR}" >"${WORK_DIR}/uploads-missing.txt"
fi

cat >"${WORK_DIR}/metadata.env" <<EOF
BACKUP_FORMAT_VERSION=1
CREATED_AT_UTC=${TIMESTAMP}
DATABASE_NAME=${DB_NAME}
UPLOAD_DIRECTORY=${UPLOAD_DIR}
HOSTNAME=$(hostname)
EOF

(
  cd "${WORK_DIR}"
  checksum_files=(mysql.sql.gz metadata.env)
  [[ -f uploads.tar.gz ]] && checksum_files+=(uploads.tar.gz)
  [[ -f uploads-missing.txt ]] && checksum_files+=(uploads-missing.txt)
  sha256sum "${checksum_files[@]}" >SHA256SUMS
  sha256sum -c SHA256SUMS
  gzip -t mysql.sql.gz
  if [[ -f uploads.tar.gz ]]; then tar -tzf uploads.tar.gz >/dev/null; fi
)

mv -- "${WORK_DIR}" "${FINAL_DIR}"
trap - EXIT

write_last_run_metric 1
if [[ -d "${NODE_EXPORTER_TEXTFILE_DIR}" && -w "${NODE_EXPORTER_TEXTFILE_DIR}" ]]; then
  backup_size="$(du -sb "${FINAL_DIR}" | awk '{print $1}')"
  {
    printf 'docflow_backup_last_success_timestamp_seconds %s\n' "$(date +%s)"
    printf 'docflow_backup_last_size_bytes %s\n' "${backup_size}"
  } >"${NODE_EXPORTER_TEXTFILE_DIR}/docflow_backup.prom.tmp"
  mv -- "${NODE_EXPORTER_TEXTFILE_DIR}/docflow_backup.prom.tmp" \
    "${NODE_EXPORTER_TEXTFILE_DIR}/docflow_backup.prom"
fi

find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' \
  -mtime "+${RETENTION_DAYS}" -exec rm -rf -- {} +

printf 'DocFlow backup completed: %s\n' "${FINAL_DIR}"
