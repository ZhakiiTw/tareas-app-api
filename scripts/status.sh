#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

api_port_from_file=""
if [[ -f .env ]]; then
  api_port_from_file="$(sed -n 's/^API_PORT=//p' .env | tail -n 1 | tr -d '\r' || true)"
fi

api_port="${API_PORT:-${api_port_from_file:-8080}}"
health_url="http://localhost:${api_port}/actuator/health"

docker compose ps

if command -v curl >/dev/null 2>&1; then
  response="$(curl -fsS "${health_url}")"
elif command -v wget >/dev/null 2>&1; then
  response="$(wget -qO- "${health_url}")"
else
  echo "ERROR: curl o wget es necesario para consultar ${health_url}" >&2
  exit 1
fi

if printf '%s' "${response}" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
  echo "API health: UP"
else
  echo "ERROR: la API no responde con estado UP en ${health_url}" >&2
  exit 1
fi
