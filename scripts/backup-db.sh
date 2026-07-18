#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ ! -f .env ]]; then
  echo "ERROR: falta .env. Copia .env.example a .env y rellena los valores antes de hacer backup." >&2
  exit 1
fi

mkdir -p backups

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="backups/tareas-postgres-${timestamp}.sql.gz"
tmp_file="${backup_file}.tmp"

cleanup() {
  rm -f "${tmp_file}"
}
trap cleanup ERR

docker compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h localhost -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "${tmp_file}"
mv "${tmp_file}" "${backup_file}"
trap - ERR

echo "Backup creado: ${backup_file}"
