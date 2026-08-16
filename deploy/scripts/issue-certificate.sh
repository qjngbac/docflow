#!/usr/bin/env bash
set -Eeuo pipefail

DOMAIN="${DOMAIN:-}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"
WEBROOT="${CERTBOT_WEBROOT:-/var/www/certbot}"

[[ -n "${DOMAIN}" ]] || { printf 'Set DOMAIN before requesting a certificate.\n' >&2; exit 1; }
[[ -n "${CERTBOT_EMAIL}" ]] || { printf 'Set CERTBOT_EMAIL before requesting a certificate.\n' >&2; exit 1; }
[[ "${DOMAIN}" =~ ^[A-Za-z0-9.-]+$ ]] || { printf 'DOMAIN contains invalid characters.\n' >&2; exit 1; }

command -v certbot >/dev/null 2>&1 || { printf 'certbot is not installed.\n' >&2; exit 1; }
mkdir -p "${WEBROOT}"

certbot certonly \
  --webroot \
  --webroot-path "${WEBROOT}" \
  --domain "${DOMAIN}" \
  --email "${CERTBOT_EMAIL}" \
  --agree-tos \
  --no-eff-email \
  --non-interactive

printf 'Certificate issued. Replace docflow.example.com in the HTTPS Nginx file, test Nginx, then reload it.\n'
