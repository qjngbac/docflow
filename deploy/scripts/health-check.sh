#!/usr/bin/env bash
set -Eeuo pipefail

JAVA_HEALTH_URL="${JAVA_HEALTH_URL:-http://127.0.0.1:9091/actuator/health/readiness}"
CRDT_HEALTH_URL="${CRDT_HEALTH_URL:-http://127.0.0.1:1235/health}"
PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_RETRY_SECONDS="${HEALTH_RETRY_SECONDS:-2}"

check_url() {
  curl --fail --silent --show-error --max-time 5 "$1" >/dev/null
}

for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt++)); do
  if check_url "${JAVA_HEALTH_URL}" && check_url "${CRDT_HEALTH_URL}"; then
    if [[ -z "${PUBLIC_HEALTH_URL}" ]] || check_url "${PUBLIC_HEALTH_URL}"; then
      printf 'DocFlow health check passed.\n'
      exit 0
    fi
  fi
  sleep "${HEALTH_RETRY_SECONDS}"
done

printf 'DocFlow health check failed after %s attempts.\n' "${HEALTH_RETRIES}" >&2
exit 1
