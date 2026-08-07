#!/usr/bin/env bash
set -Eeuo pipefail

# =====================================================================
# cd-deploy.sh — Despliega una imagen de GHCR en el servicio `api` de
# tareas-app-api (produccion). SOLO se actualiza el servicio api.
#
# Exit codes:
#   0 = deploy correcto
#   1 = error operacional ANTES de modificar el servicio
#       (imagen inexistente, git, pull, lock ocupado, compose up fallo)
#   2 = uso invalido / SHA invalido
#   3 = deploy falla pero el rollback funciona
#   4 = deploy falla Y el rollback falla (CRITICO)
#
# Estados 3 y 4: el SHA se registra en .failed-sha y NO se reintenta
# automaticamente. Reintento manual:
#   scripts/cd-deploy.sh "$(cat .failed-sha)"
#
# PROHIBIDO: docker compose down. No se toca postgres ni cloudflared
# (--no-deps + proyecto compose aislado). No se tocan volumenes ni
# secretos distintos de API_IMAGE_TAG (solo se actualiza esa variable
# del .env; el resto del .env se conserva y se respalda antes).
# =====================================================================

PROJECT_DIR="/srv/docker/tareas-app-api"
IMAGE="ghcr.io/marfern2/tareas-app-api"
ENV_FILE="${PROJECT_DIR}/.env"
BACKUP_DIR="${PROJECT_DIR}/backups-antes-deploy"
LOCKFILE="${PROJECT_DIR}/.cd-deploy.lock"
MARKER="${PROJECT_DIR}/.deployed-sha"
FAILED_MARKER="${PROJECT_DIR}/.failed-sha"
LOG_DIR="${PROJECT_DIR}/logs"
LOG_FILE="${LOG_DIR}/cd-deploy.log"

LOCAL_HEALTH="http://127.0.0.1:8080/actuator/health"
PUBLIC_HEALTH="https://donit-api.marfern.dev/actuator/health"

log() { printf '%s %s\n' "$(date -Is)" "$*" | tee -a "${LOG_FILE}"; }

usage() {
  echo "Uso: $0 <sha-completo-40hex>" >&2
  echo "  La imagen objetivo es ${IMAGE}:<sha12>" >&2
  echo "  Reintento manual de un SHA fallido:" >&2
  echo "    $0 \"\$(cat ${FAILED_MARKER})\"" >&2
  exit 2
}

# ---- 1. Uso / validacion SHA -------------------------------------------
target="${1:-}"
if [[ ! "${target}" =~ ^[0-9a-f]{40}$ ]]; then
  log "ERROR: SHA invalido (${target:-vacio})"
  usage
fi
short="${target:0:12}"

mkdir -p "${LOG_DIR}"

# ---- 2. Lock exclusivo (anti-paralelo) -----------------------------------
exec 9>"${LOCKFILE}"
if ! flock -n 9; then
  log "ESTADO=1 otro deploy en curso (flock ocupado)"
  exit 1
fi

# ---- 3. Sync seguro de compose.yaml + scripts/ desde origin/master --------
# NO se usa git reset --hard ni git clean -fd. Se extraen SOLO los ficheros
# que necesita el deploy (compose.yaml y scripts/) desde origin/master con
# `git archive`, que nunca borra y no toca el indice.
# NO se sobrescriben: .env, logs/, backups/, backups-antes-deploy/,
# .deployed-sha, .failed-sha, .cd-deploy.lock, src/ ni ningun otro fichero.
if ! git fetch origin master --quiet 2>>"${LOG_FILE}"; then
  log "ESTADO=1 git fetch fallo"
  exit 1
fi
if ! git archive --format=tar origin/master compose.yaml scripts/ 2>>"${LOG_FILE}" \
     | tar -x -C "${PROJECT_DIR}" 2>>"${LOG_FILE}"; then
  log "ESTADO=1 sincronizacion de compose.yaml/scripts fallo"
  exit 1
fi
chmod +x "${PROJECT_DIR}"/scripts/*.sh 2>/dev/null || true
log "sync: compose.yaml y scripts/ actualizados desde origin/master"

# ---- 4. Re-check de imagen GHCR (sin descargar) ---------------------------
if ! docker manifest inspect "${IMAGE}:${short}" >/dev/null 2>&1; then
  log "ESTADO=1 imagen inexistente ${IMAGE}:${short}"
  exit 1
fi

# ---- 5. Tag anterior + backup completo de .env -----------------------------
prev_tag="$(sed -n 's/^API_IMAGE_TAG=//p' "${ENV_FILE}" | tail -n1 | tr -d '\r' || true)"
prev_short="${prev_tag:0:12}"
if [[ -z "${prev_short}" && -f "${MARKER}" ]]; then
  prev_short="$(head -c 12 "${MARKER}")"
fi
# La imagen anterior se conserva localmente (no se elimina ninguna imagen).

mkdir -p "${BACKUP_DIR}"
backup_env="${BACKUP_DIR}/.env-$(date +%Y%m%d-%H%M%S).bak"
if ! cp -a "${ENV_FILE}" "${backup_env}" 2>/dev/null; then
  log "ESTADO=1 no se pudo respaldar .env"
  exit 1
fi
chmod 600 "${backup_env}"
log "backup .env -> ${backup_env}"

# ---- 6. Actualizacion ATOMICA SOLO de API_IMAGE_TAG -------------------------
awk -v t="${short}" '
  /^API_IMAGE_TAG=/ { seen=1; print "API_IMAGE_TAG=" t; next }
  { print }
  END { if (!seen) print "API_IMAGE_TAG=" t }
' "${ENV_FILE}" > "${ENV_FILE}.tmp" && mv "${ENV_FILE}.tmp" "${ENV_FILE}"
chmod 600 "${ENV_FILE}"
log "API_IMAGE_TAG=${short} (prev=${prev_short:-<sin previo conocido>})"

# ---- 7. Pull + recreate SOLO api ---------------------------------------------
if ! docker compose pull api; then
  log "ESTADO=1 pull fallo de ${IMAGE}:${short}"
  cp -a "${backup_env}" "${ENV_FILE}" && chmod 600 "${ENV_FILE}"
  exit 1
fi

if ! docker compose up -d --no-deps api >>"${LOG_FILE}" 2>&1; then
  log "ESTADO=1 docker compose up -d --no-deps api fallo"
  cp -a "${backup_env}" "${ENV_FILE}" && chmod 600 "${ENV_FILE}"
  exit 1
fi
log "api recreado con ${short}"

# ---- 8. Health checks ---------------------------------------------------------
check_health() {
  local url="$1" attempts="${2:-24}" delay="${3:-5}" resp
  for ((i = 1; i <= attempts; i++)); do
    if resp="$(curl -fsS --max-time 5 "${url}" 2>/dev/null)" \
       && printf '%s' "${resp}" | jq -e '.status == "UP"' >/dev/null 2>&1; then
      return 0
    fi
    sleep "${delay}"
  done
  return 1
}

rollback() {
  log "ROLLBACK: restaurando estado previo"
  if [[ -n "${backup_env:-}" && -f "${backup_env}" ]]; then
    cp -a "${backup_env}" "${ENV_FILE}" && chmod 600 "${ENV_FILE}"
    log "restaurado .env desde ${backup_env}"
  fi
  if [[ -n "${prev_short:-}" ]] && ! grep -q '^API_IMAGE_TAG=' "${ENV_FILE}"; then
    printf '\nAPI_IMAGE_TAG=%s\n' "${prev_short}" >>"${ENV_FILE}"
    chmod 600 "${ENV_FILE}"
  fi
  if [[ -z "${prev_short:-}" ]]; then
    log "AVISO: no hay despliegue previo conocido; el rollback queda al tag por defecto (master). Accion manual recomendada."
  fi
  docker compose up -d --no-deps api >/dev/null 2>&1 || true
  sleep 15
}

fail_state() {
  local st="$1"
  shift
  printf '%s' "${target}" >"${FAILED_MARKER}"
  chmod 600 "${FAILED_MARKER}"
  log "ESTADO=${st} $* — SHA registrado en ${FAILED_MARKER}"
  exit "${st}"
}

if ! check_health "${LOCAL_HEALTH}" 30 5; then
  log "FALLO health local"
  rollback
  if check_health "${LOCAL_HEALTH}" 24 5; then
    fail_state 3 "deploy falla, rollback OK (health local UP)"
  else
    fail_state 4 "CRITICO: rollback FALLIDO, health local DOWN"
  fi
fi
log "health local OK"

if ! check_health "${PUBLIC_HEALTH}" 12 10; then
  log "FALLO health publico"
  rollback
  if check_health "${PUBLIC_HEALTH}" 12 10 && check_health "${LOCAL_HEALTH}" 12 5; then
    fail_state 3 "deploy falla, rollback OK (health publico UP)"
  else
    fail_state 4 "CRITICO: rollback FALLIDO"
  fi
fi
log "health publico OK"

# ---- 9. Exito -----------------------------------------------------------------
printf '%s' "${target}" >"${MARKER}"
chmod 600 "${MARKER}"
rm -f "${FAILED_MARKER}"
log "ESTADO=0 DEPLOY OK sha=${target} (tag ${short})"
exit 0
