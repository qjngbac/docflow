#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT="${APP_ROOT:-/opt/docflow}"
CURRENT_LINK="${CURRENT_LINK:-${APP_ROOT}/current}"
STATE_DIR="${DEPLOY_STATE_DIR:-/var/lib/docflow-deploy}"
HEALTH_SCRIPT="${HEALTH_SCRIPT:-${APP_ROOT}/deploy/scripts/health-check.sh}"
SERVICES="${DOCFLOW_SERVICES:-docflow-server docflow-collab}"

[[ "${ROLLBACK_CONFIRM:-}" == "ROLLBACK_DOCFLOW" ]] || {
  printf 'Set ROLLBACK_CONFIRM=ROLLBACK_DOCFLOW to confirm application rollback.\n' >&2
  exit 1
}
[[ -f "${STATE_DIR}/previous-release" ]] || { printf 'No previous release is recorded.\n' >&2; exit 1; }

previous="$(cat "${STATE_DIR}/previous-release")"
previous="$(readlink -f "${previous}")"
[[ -d "${previous}" && "${previous}" == "${APP_ROOT}/releases/"* ]] || {
  printf 'The recorded previous release is invalid.\n' >&2
  exit 1
}

current=""
if [[ -L "${CURRENT_LINK}" ]]; then current="$(readlink -f "${CURRENT_LINK}")"; fi
ln -sfn "${previous}" "${CURRENT_LINK}.next"
mv -Tf "${CURRENT_LINK}.next" "${CURRENT_LINK}"
if [[ -n "${current}" ]]; then printf '%s\n' "${current}" >"${STATE_DIR}/previous-release"; fi

systemctl restart ${SERVICES}
"${HEALTH_SCRIPT}"

printf 'DocFlow application rolled back to: %s\n' "${previous}"
printf 'Database data was not changed. Use restore.sh only after stopping writes and confirming migration incompatibility.\n'
