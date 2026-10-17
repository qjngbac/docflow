#!/usr/bin/env bash
# Run production scripts with fixture paths and mocked services/database commands.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
mkdir -p "${ROOT}/.tmp"
FIXTURE="$(mktemp -d "${ROOT}/.tmp/deploy-test.XXXXXX")"
cleanup() {
  local resolved
  resolved="$(cd "${FIXTURE}" && pwd -P)"
  case "${resolved}" in "${ROOT}/.tmp/deploy-test."*) rm -rf -- "${resolved}" ;; *) return 1 ;; esac
}
trap cleanup EXIT
# Git Bash can represent links as MSYS shortcuts without Windows symlink privileges.
case "${OSTYPE}" in msys*) export MSYS="${MSYS:-} winsymlinks:lnk" ;; esac
mkdir -p "${FIXTURE}/bin"
export PATH="${FIXTURE}/bin:${PATH}"

cat >"${FIXTURE}/bin/systemctl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"${CASE_DIR}/systemctl.log"
count="$(wc -l <"${CASE_DIR}/systemctl.log")"
if [[ "${TEST_MODE}" == restart-fails && ${count} -eq 1 ]]; then exit 23; fi
if [[ "${TEST_MODE}" == rollback-restart-fails && ${count} -eq 2 ]]; then exit 24; fi
EOF
cat >"${FIXTURE}/bin/health" <<'EOF'
#!/usr/bin/env bash
printf 'check\n' >>"${CASE_DIR}/health.log"
count="$(wc -l <"${CASE_DIR}/health.log")"
[[ "${TEST_MODE}" == success ]] && exit 0
[[ "${TEST_MODE}" == restart-fails && ${count} -eq 1 ]] && exit 0
[[ "${TEST_MODE}" == rollback-health-fails ]] && exit 1
[[ ${count} -gt 1 ]] && exit 0
exit 1
EOF
cat >"${FIXTURE}/bin/backup" <<'EOF'
#!/usr/bin/env bash
printf 'backup\n' >>"${CASE_DIR}/backup.log"
EOF
cat >"${FIXTURE}/bin/mysqldump" <<'EOF'
#!/usr/bin/env bash
printf 'dump\n' >>"${CASE_DIR}/database.log"
printf 'CREATE TABLE fixture (id INT);\n'
EOF
cat >"${FIXTURE}/bin/mysql" <<'EOF'
#!/usr/bin/env bash
printf 'restore\n' >>"${CASE_DIR}/database.log"
cat >/dev/null
EOF
cat >"${FIXTURE}/bin/flock" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${FIXTURE}/bin/"*

failures=0
check() { if ! "$@"; then printf 'FAIL: %s\n' "$*" >&2; failures=$((failures + 1)); fi; }
line_count() { if [[ -f "$1" ]]; then wc -l <"$1"; else printf '0\n'; fi; }

release_case() {
  export TEST_MODE="$1" CASE_DIR="${FIXTURE}/$1"
  export APP_ROOT="${CASE_DIR}/app" DEPLOY_STATE_DIR="${CASE_DIR}/state"
  export CURRENT_LINK="${APP_ROOT}/current" BACKUP_SCRIPT="${FIXTURE}/bin/backup" HEALTH_SCRIPT="${FIXTURE}/bin/health"
  export DOCFLOW_SERVICES='fixture-server fixture-collab'
  mkdir -p "${APP_ROOT}/releases/old" "${APP_ROOT}/releases/new/docflow-collab/src" "${APP_ROOT}/releases/new/docflow-web"
  touch "${APP_ROOT}/releases/new/docflow-server.jar" "${APP_ROOT}/releases/new/docflow-collab/src/server.js" "${APP_ROOT}/releases/new/docflow-web/index.html"
  ln -s "${APP_ROOT}/releases/old" "${CURRENT_LINK}"
  status=0
  bash "${ROOT}/deploy/scripts/deploy-release.sh" "${APP_ROOT}/releases/new" >"${CASE_DIR}/output.log" 2>&1 || status=$?
  check test "$(line_count "${CASE_DIR}/backup.log")" -eq 1
  if [[ "${TEST_MODE}" == success ]]; then
    check test "${status}" -eq 0
    check test "$(readlink -f "${CURRENT_LINK}")" = "${APP_ROOT}/releases/new"
    check test "$(line_count "${CASE_DIR}/systemctl.log")" -eq 1
  else
    check test "${status}" -ne 0
    check test "$(readlink -f "${CURRENT_LINK}")" = "${APP_ROOT}/releases/old"
    check test "$(line_count "${CASE_DIR}/systemctl.log")" -eq 2
    if [[ "${TEST_MODE}" == restart-fails ]]; then
      check test "$(line_count "${CASE_DIR}/health.log")" -eq 1
    elif [[ "${TEST_MODE}" == rollback-restart-fails ]]; then
      check grep -q 'rollback service restart failed' "${CASE_DIR}/output.log"
    elif [[ "${TEST_MODE}" == rollback-health-fails ]]; then
      check grep -q 'previous release is also unhealthy' "${CASE_DIR}/output.log"
    else
      check test "$(line_count "${CASE_DIR}/health.log")" -eq 2
    fi
  fi
  printf 'Checked release: %s\n' "${TEST_MODE}"
}

for mode in success restart-fails health-fails rollback-restart-fails rollback-health-fails; do release_case "${mode}"; done

export CASE_DIR="${FIXTURE}/missing-uploads"
export BACKUP_ROOT="${CASE_DIR}/backups" UPLOAD_DIR="${CASE_DIR}/uploads"
export MYSQL_DEFAULTS_FILE="${FIXTURE}/mysql.cnf" NODE_EXPORTER_TEXTFILE_DIR="${FIXTURE}/metrics"
touch "${MYSQL_DEFAULTS_FILE}"
mkdir -p "${CASE_DIR}" "${NODE_EXPORTER_TEXTFILE_DIR}"
status=0
bash "${ROOT}/deploy/scripts/backup.sh" >"${CASE_DIR}/output.log" 2>&1 || status=$?
check test "${status}" -ne 0
check test "$(line_count "${CASE_DIR}/database.log")" -eq 0
check grep -q 'upload directory does not exist' "${CASE_DIR}/output.log"
printf 'Checked backup: missing upload directory\n'

export CASE_DIR="${FIXTURE}/empty-uploads"
export BACKUP_ROOT="${CASE_DIR}/backups" UPLOAD_DIR="${CASE_DIR}/uploads"
mkdir -p "${UPLOAD_DIR}"
status=0
bash "${ROOT}/deploy/scripts/backup.sh" >"${CASE_DIR}/output.log" 2>&1 || status=$?
check test "${status}" -eq 0
backup_dir="$(find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -name '20*T*Z' | head -n 1)"
check test -f "${backup_dir}/uploads.tar.gz"
check bash "${ROOT}/deploy/scripts/verify-backup.sh" "${backup_dir}"
# Explicit database-only restoration remains supported for complete backups.
RESTORE_CONFIRM=RESTORE_DOCFLOW RESTORE_UPLOADS=false check bash "${ROOT}/deploy/scripts/restore.sh" "${backup_dir}"
check test "$(line_count "${CASE_DIR}/database.log")" -eq 2
printf 'Checked backup: existing empty upload directory and database-only restore\n'

# A checksummed SQL dump without uploads is not a complete restorable backup.
incomplete="${FIXTURE}/incomplete"
mkdir -p "${incomplete}"
cp "${backup_dir}/mysql.sql.gz" "${incomplete}/mysql.sql.gz"
(cd "${incomplete}" && sha256sum mysql.sql.gz >SHA256SUMS)
status=0
bash "${ROOT}/deploy/scripts/verify-backup.sh" "${incomplete}" >"${FIXTURE}/verify.log" 2>&1 || status=$?
check test "${status}" -ne 0
before="$(line_count "${CASE_DIR}/database.log")"
status=0
RESTORE_CONFIRM=RESTORE_DOCFLOW bash "${ROOT}/deploy/scripts/restore.sh" "${incomplete}" >"${FIXTURE}/restore.log" 2>&1 || status=$?
check test "${status}" -ne 0
check test "$(line_count "${CASE_DIR}/database.log")" -eq "${before}"
printf 'Checked verification: incomplete backup rejected before database restore\n'

[[ ${failures} -eq 0 ]] || { printf '%s deployment regression assertions failed\n' "${failures}" >&2; exit 1; }
printf 'All deployment regression checks passed.\n'
