#!/usr/bin/env bash
set -Eeuo pipefail

# Backup logico (pg_dump --format=custom) de PostgreSQL para tareas-app-api.
# Se ejecuta dentro del contenedor tareas-postgres sin detener nada.
# No imprime secretos. Escribe primero a un temporal y valida antes de renombrar.

CONTAINER="tareas-postgres"
BACKUP_DIR="/srv/docker/backups/tareas-app-postgres"
RETENTION_DAYS=14
MIN_BYTES=1024
MIN_FREE_BYTES=$((512 * 1024 * 1024))
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/tareas-app-${TIMESTAMP}.dump"
TMP_FILE="${BACKUP_FILE}.tmp.$$"
STDERR_FILE="/tmp/tareas-pgdump-stderr.$$"
VALIDATE_PATH="/tmp/tareas-backup-check.dump"

log()   { echo "[backup-db $(date '+%Y-%m-%d %H:%M:%S')] $*"; }
error() { echo "[backup-db $(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2; }

cleanup() {
  if [[ -n "${TMP_FILE:-}" && -e "${TMP_FILE}" ]]; then
    rm -f "${TMP_FILE}"
    log "Temporal local eliminado: ${TMP_FILE}"
  fi
  rm -f "${STDERR_FILE:-/tmp/tareas-pgdump-stderr.$$}"
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${CONTAINER}"; then
    docker exec "${CONTAINER}" rm -f "${VALIDATE_PATH}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

main() {
  local start_time duration actual_size avail health db_name db_user hash count deleted
  start_time="$(date +%s)"
  log "Inicio del backup"

  # 1. Directorio de backups (fuera del repositorio Git)
  if ! mkdir -p "${BACKUP_DIR}" 2>/dev/null; then
    error "No se puede crear ${BACKUP_DIR}"
    exit 1
  fi
  chmod 700 "${BACKUP_DIR}"

  # 2. Docker disponible y contenedor en ejecucion
  if ! command -v docker >/dev/null 2>&1; then
    error "Docker no esta disponible"
    exit 1
  fi
  if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${CONTAINER}"; then
    error "El contenedor ${CONTAINER} no esta en ejecucion"
    exit 1
  fi

  # 3. PostgreSQL healthy
  health="$(docker inspect --format '{{.State.Health.Status}}' "${CONTAINER}" 2>/dev/null || true)"
  if [[ "${health}" != "healthy" ]]; then
    if ! docker exec "${CONTAINER}" pg_isready -h localhost >/dev/null 2>&1; then
      error "PostgreSQL no responde (health='${health}')"
      exit 1
    fi
    log "Health='${health}' pero pg_isready OK; se continua"
  fi

  # 4. Nombre de BD y usuario desde el contenedor (sin contraseñas)
  db_name="$(docker exec "${CONTAINER}" sh -c 'printf "%s" "$POSTGRES_DB"')"
  db_user="$(docker exec "${CONTAINER}" sh -c 'printf "%s" "$POSTGRES_USER"')"
  if [[ -z "${db_name}" || -z "${db_user}" ]]; then
    error "No se pudieron leer POSTGRES_DB/POSTGRES_USER del contenedor"
    exit 1
  fi
  log "Base de datos: ${db_name} / usuario: ${db_user} (nombres no sensibles)"

  # 5. Espacio libre en disco
  avail="$(df --output=avail -B1 "${BACKUP_DIR}" 2>/dev/null | tail -n1 | tr -d ' ' || true)"
  if [[ -n "${avail}" && "${avail}" -lt "${MIN_FREE_BYTES}" ]]; then
    error "Espacio libre insuficiente en ${BACKUP_DIR}: $(numfmt --to=iec "${avail}" 2>/dev/null || echo "${avail} bytes")"
    exit 1
  fi
  log "Espacio libre en ${BACKUP_DIR}: $(numfmt --to=iec "${avail}" 2>/dev/null || echo "${avail} bytes")"

  # 6. pg_dump custom dentro del contenedor (no detiene la base)
  if ! docker exec "${CONTAINER}" sh -c \
       'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -h localhost -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges --compress=9' \
       > "${TMP_FILE}" 2>"${STDERR_FILE}"; then
    error "pg_dump fallo; se descarta el temporal"
    sed 's/^/  pg_dump: /' "${STDERR_FILE}" >&2 || true
    exit 1
  fi

  # 7. Validacion del temporal (existe, tamano minimo y pg_restore --list)
  actual_size="$(stat -c %s "${TMP_FILE}")"
  if [[ "${actual_size}" -lt "${MIN_BYTES}" ]]; then
    error "Tamano sospechoso (${actual_size} bytes < ${MIN_BYTES})"
    exit 1
  fi
  if ! docker cp "${TMP_FILE}" "${CONTAINER}:${VALIDATE_PATH}" >/dev/null 2>&1; then
    error "No se pudo copiar el dump al contenedor para validarlo"
    exit 1
  fi
  if ! docker exec "${CONTAINER}" pg_restore --list "${VALIDATE_PATH}" >/dev/null 2>&1; then
    error "pg_restore --list no pudo leer el dump (posible corrupcion)"
    exit 1
  fi
  log "Validacion OK: ${actual_size} bytes y pg_restore --list lo lee"

  # 8. Renombrar al nombre definitivo y permisos
  mv "${TMP_FILE}" "${BACKUP_FILE}"
  chmod 600 "${BACKUP_FILE}"
  log "Permisos: $(stat -c '%a %U:%G' "${BACKUP_FILE}")"

  # 9. Retencion: borrar solo el patron exacto y loguear lo borrado
  deleted="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'tareas-app-*.dump' -mtime "+${RETENTION_DAYS}" -print -delete)"
  if [[ -n "${deleted}" ]]; then
    while IFS= read -r f; do
      log "Retencion: eliminado ${f} (mas de ${RETENTION_DAYS} dias)"
    done <<< "${deleted}"
  fi

  # 10. Resumen final
  duration="$(( $(date +%s) - start_time ))s"
  hash="$(sha256sum "${BACKUP_FILE}" | awk '{print $1}')"
  count="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'tareas-app-*.dump' | wc -l)"
  log "Backup OK: ${BACKUP_FILE} ($(stat -c %s "${BACKUP_FILE}") bytes, ${duration}, sha256=${hash})"
  log "Total de backups retenidos: ${count}"
}

main
