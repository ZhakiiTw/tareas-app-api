#!/usr/bin/env bash
set -Eeuo pipefail

# =====================================================================
# cd-poll.sh — Poller del CD. En el futuro lo ejecuta un timer systemd
# en el servidor (NO activado en este PR).
#
# Comportamiento:
#   1. SHA actual de origin/master (git ls-remote, solo lectura saliente).
#   2. Si SHA == .deployed-sha  -> exit 0.
#   3. Si SHA == .failed-sha    -> exit 0 (no reintentar automaticamente).
#   4. Si la imagen ghcr.io/...:<sha12> todavia NO existe en GHCR
#      -> exit 0. NO es un fallo: el workflow deploy.yml aun esta
#      construyendo/publicando. Se espera al siguiente poll.
#   5. Si existe -> ejecuta scripts/cd-deploy.sh <sha>.
#
# El anti-paralelismo lo garantiza cd-deploy.sh con flock exclusivo.
# =====================================================================

PROJECT_DIR="/srv/docker/tareas-app-api"
IMAGE="ghcr.io/marfern2/tareas-app-api"
DEPLOYED_SHA_FILE="${PROJECT_DIR}/.deployed-sha"
FAILED_SHA_FILE="${PROJECT_DIR}/.failed-sha"

cd "${PROJECT_DIR}"

deployed="$(cat "${DEPLOYED_SHA_FILE}" 2>/dev/null || true)"
failed="$(cat "${FAILED_SHA_FILE}" 2>/dev/null || true)"

master_sha="$(git ls-remote origin master 2>/dev/null | awk '{print $1}')"
if [[ ! "${master_sha}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "[cd-poll $(date -Is)] ERROR: no se pudo obtener el SHA de origin/master" >&2
  exit 1
fi

# Ya desplegado -> nada que hacer
[[ "${master_sha}" == "${deployed}" ]] && exit 0

# SHA fallido conocido -> no reintentar automaticamente (retry manual: cd-deploy.sh)
[[ "${master_sha}" == "${failed}" ]] && exit 0

short="${master_sha:0:12}"

# Imagen aun no publicada -> NO es un fallo de deploy. Se espera al siguiente poll.
if ! err="$(docker manifest inspect "${IMAGE}:${short}" 2>&1)"; then
  echo "[cd-poll $(date -Is)] imagen ${IMAGE}:${short} aun no publicada (${err}); se espera al siguiente poll"
  exit 0
fi

exec ./scripts/cd-deploy.sh "${master_sha}"
