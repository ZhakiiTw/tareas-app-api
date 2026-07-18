#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -f .env ]]; then
  echo "ERROR: falta .env. Copia .env.example a .env y rellena los valores antes de desplegar." >&2
  exit 1
fi

docker compose build
docker compose up -d
docker compose ps
