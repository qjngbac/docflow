#!/usr/bin/env bash
set -Eeuo pipefail

RELEASE_DIR="${1:-}"
APP_ROOT="${APP_ROOT:-/opt/docflow}"
CURRENT_LINK="${CURRENT_LINK:-${APP_ROOT}/current}"
STATE_DIR="${DEPLOY_STATE_DIR:-/var/lib/docflow-deploy}"
BACKUP_SCRIPT="${BACKUP_SCRIPT:-${APP_ROOT}/deploy/scripts/backup.sh}"
HEALTH_SCRIPT="${HEALTH_SCRIPT:-${APP_ROOT}/deploy/scripts/health-check.sh}"
SERVICES="${DOCFLOW_SERVICES:-docflow-server docflow-collab}"

fail() {
  printf 'DocFlow release failed: %s\n' "$*" >&2
  exit 1
}

[[ -n "${RELEASE_DIR}" && -d "${RELEASE_DIR}" ]] || fail "usage: deploy-release.sh <prepared-release-directory>"
RELEASE_DIR="$(readlink -f "${RELEASE_DIR}")"
[[ "${RELEASE_DIR}" == "${APP_ROOT}/releases/"* ]] || fail "release must be under ${APP_ROOT}/releases"
[[ -f "${RELEASE_DIR}/docflow-server.jar" ]] || fail "docflow-server.jar is missing"
[[ -f "${RELEASE_DIR}/docflow-collab/src/server.js" ]] || fail "docflow-collab is missing"
[[ -f "${RELEASE_DIR}/docflow-web/index.html" ]] || fail "built frontend is missing"

mkdir -p "${STATE_DIR}"
previous=""
if [[ -L "${CURRENT_LINK}" ]]; then previous="$(readlink -f "${CURRENT_LINK}")"; fi

"${BACKUP_SCRIPT}"

if [[ -n "${previous}" ]]; then printf '%s\n' "${previous}" >"${STATE_DIR}/previous-release"; fi
ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}.next"
mv -Tf "${CURRENT_LINK}.next" "${CURRENT_LINK}"

systemctl restart ${SERVICES}
if ! "${HEALTH_SCRIPT}"; then
  if [[ -n "${previous}" && -d "${previous}" ]]; then
    ln -sfn "${previous}" "${CURRENT_LINK}.next"
    mv -Tf "${CURRENT_LINK}.next" "${CURRENT_LINK}"
    systemctl restart ${SERVICES}
  fi
  fail "new release was unhealthy; the application link was returned to the previous release"
fi

printf 'DocFlow release activated: %s\n' "${RELEASE_DIR}"
