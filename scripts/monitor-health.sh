#!/usr/bin/env bash
set -uo pipefail

# =====================================================================
# monitor-health.sh — Monitor read-only de salud de tareas-app-api y del
# servidor (marserver).
#
# Propiedades:
#   - ESTRICTAMENTE READ-ONLY. NO modifica contenedores, servicios,
#     timers, backups, logs ni .env. NO hace deploy ni auto-reparacion.
#   - Unica escritura permitida: su propio fichero de estado
#     logs/.monitor-state (creacion atomica vía temporal + mv).
#   - Ejecuta TODOS los checks aunque alguno falle (no usa set -e).
#   - Estados agregados: OK=0, WARNING=1, CRITICAL=2 (peor encontrado).
#   - Salida por lineas a stdout/stderr para journald.
#
# Exit codes:
#   0 = OK
#   1 = WARNING
#   2 = CRITICAL
#
# Dependencias (ya presentes en el host, no añade paquetes):
#   bash, curl, jq, docker, systemctl, stat, df, awk, grep, date
#
# Overrides por environment (para simulaciones y futuros timers):
#   HTTP_RETRIES, HTTP_TIMEOUT_LOCAL, HTTP_TIMEOUT_PUBLIC,
#   HTTP_DELAY_LOCAL, HTTP_DELAY_PUBLIC,
#   DISK_WARN, DISK_CRIT, INODE_WARN, INODE_CRIT,
#   BACKUP_WARN_HOURS, BACKUP_CRIT_HOURS,
#   PROJECT_DIR, BACKUP_DIR, STATE_FILE, FAILED_SHA_FILE, CD_LOG_FILE,
#   LOCAL_HEALTH_URL, PUBLIC_HEALTH_URL, CF_READY_URL,
#   CLOUDFLARED_READY_RETRIES, CLOUDFLARED_READY_TIMEOUT, CLOUDFLARED_READY_DELAY
# =====================================================================

# ---- Configuracion (umbrales con defaults) ---------------------------
HTTP_RETRIES="${HTTP_RETRIES:-3}"
HTTP_TIMEOUT_LOCAL="${HTTP_TIMEOUT_LOCAL:-5}"
HTTP_TIMEOUT_PUBLIC="${HTTP_TIMEOUT_PUBLIC:-15}"
HTTP_DELAY_LOCAL="${HTTP_DELAY_LOCAL:-5}"
HTTP_DELAY_PUBLIC="${HTTP_DELAY_PUBLIC:-10}"

DISK_WARN="${DISK_WARN:-85}"
DISK_CRIT="${DISK_CRIT:-92}"
INODE_WARN="${INODE_WARN:-85}"
INODE_CRIT="${INODE_CRIT:-92}"

BACKUP_WARN_HOURS="${BACKUP_WARN_HOURS:-30}"
BACKUP_CRIT_HOURS="${BACKUP_CRIT_HOURS:-52}"
BACKUP_MIN_BYTES="${BACKUP_MIN_BYTES:-1024}"

# Rutas (override por env para simulaciones seguras)
PROJECT_DIR="${PROJECT_DIR:-/srv/docker/tareas-app-api}"
BACKUP_DIR="${BACKUP_DIR:-/srv/docker/backups/tareas-app-postgres}"
STATE_FILE="${STATE_FILE:-${PROJECT_DIR}/logs/.monitor-state}"
FAILED_SHA_FILE="${FAILED_SHA_FILE:-${PROJECT_DIR}/.failed-sha}"
CD_LOG_FILE="${CD_LOG_FILE:-${PROJECT_DIR}/logs/cd-deploy.log}"

LOCAL_HEALTH_URL="${LOCAL_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
PUBLIC_HEALTH_URL="${PUBLIC_HEALTH_URL:-https://donit-api.marfern.dev/actuator/health}"

# Endpoint /ready de cloudflared (loopback, metrics). Read-only.
CF_READY_URL="${CF_READY_URL:-http://127.0.0.1:20241/ready}"
CLOUDFLARED_READY_RETRIES="${CLOUDFLARED_READY_RETRIES:-3}"
CLOUDFLARED_READY_TIMEOUT="${CLOUDFLARED_READY_TIMEOUT:-5}"
CLOUDFLARED_READY_DELAY="${CLOUDFLARED_READY_DELAY:-2}"

BACKUP_TIMER_USER="tareas-app-db-backup.timer"
BACKUP_SERVICE_USER="tareas-app-db-backup.service"
CD_TIMER="tareas-app-cd.timer"
CD_SERVICE="tareas-app-cd.service"

# systemd user de mar: XDG_RUNTIME_DIR permite consultar el manager de usuario.
XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export XDG_RUNTIME_DIR

# ---- Contadores -------------------------------------------------------
N_OK=0
N_WARN=0
N_CRIT=0

# ---- Helpers ----------------------------------------------------------
add_result() {
  local sev="$1" check="$2" msg="$3"
  case "${sev}" in
    CRITICAL) N_CRIT=$((N_CRIT + 1)) ;;
    WARNING)  N_WARN=$((N_WARN + 1)) ;;
    *)        N_OK=$((N_OK + 1)) ;;
  esac
  printf '[monitor-health] %-4s %-14s %s\n' "${sev}" "${check}" "${msg}"
}

# check_http url retries timeout delay -> 0 si HTTP 200 + JSON status UP
check_http() {
  local url="$1" retries="$2" timeout="$3" delay="$4" i resp
  for ((i = 1; i <= retries; i++)); do
    if resp="$(curl -fsS --max-time "${timeout}" "${url}" 2>/dev/null)" \
       && printf '%s' "${resp}" | jq -e '.status == "UP"' >/dev/null 2>&1; then
      return 0
    fi
    [[ "${i}" -lt "${retries}" ]] && sleep "${delay}"
  done
  return 1
}

# check_cloudflared_ready retries timeout delay
#   Consulta GET /ready (loopback, read-only). No usa check_http: ese exige
#   .status == "UP" (Actuator) y aqui el contrato es .status == 200 numerico +
#   .readyConnections >= 1. Establece:
#     CF_READY_OK     -> 1 si el endpoint esta listo
#     CF_READY_STATE  -> ok | degraded (conns==0) | down (no responde/JSON invalido)
#     CF_READY_CONNS  -> readyConnections numerico (o vacio)
#   readyConnections==0 es un estado definitivo: no se reintenta.
#   down se reintenta (transitorio) hasta agotar retries.
check_cloudflared_ready() {
  local retries="$1" timeout="$2" delay="$3" i resp
  CF_READY_OK=0
  CF_READY_STATE="down"
  CF_READY_CONNS=""
  for ((i = 1; i <= retries; i++)); do
    if resp="$(curl -fsS --max-time "${timeout}" "${CF_READY_URL}" 2>/dev/null)"; then
      if printf '%s' "${resp}" | jq -e '.status == 200 and (.readyConnections | type == "number") and (.readyConnections >= 1)' >/dev/null 2>&1; then
        CF_READY_OK=1
        CF_READY_STATE="ok"
        CF_READY_CONNS="$(printf '%s' "${resp}" | jq -r '.readyConnections' 2>/dev/null || true)"
        return 0
      fi
      if printf '%s' "${resp}" | jq -e '.status == 200 and (.readyConnections | type == "number") and (.readyConnections == 0)' >/dev/null 2>&1; then
        CF_READY_STATE="degraded"
        CF_READY_CONNS="0"
        return 1
      fi
    fi
    [[ "${i}" -lt "${retries}" ]] && sleep "${delay}"
  done
  return 1
}

# classify_restart prev_id prev_base cur_id cur_restarts
#   -> RESET (container nuevo), OK, WARN (+1/+2), CRIT (>=+3)
classify_restart() {
  local prev_id="$1" prev_base="$2" cur_id="$3" cur_restarts="$4" delta
  [[ -z "${prev_base}" ]] && prev_base=0
  [[ -z "${cur_restarts}" ]] && cur_restarts=0
  if [[ "${cur_id}" != "${prev_id}" ]]; then
    printf 'RESET'
    return
  fi
  delta=$(( cur_restarts - prev_base ))
  if (( delta >= 3 )); then
    printf 'CRIT'
  elif (( delta >= 1 )); then
    printf 'WARN'
  else
    printf 'OK'
  fi
}

# ---- Estado .monitor-state --------------------------------------------
STATE_API_ID=""
STATE_RESTART_BASELINE=""
STATE_LAST=""

load_state() {
  [[ -f "${STATE_FILE}" ]] || return 0
  STATE_API_ID="$(grep -E '^api_container_id=' "${STATE_FILE}" 2>/dev/null | head -n1 | cut -d= -f2- || true)"
  # El baseline de restarts se fuerza numerico (tolera estado corrupto).
  STATE_RESTART_BASELINE="$(grep -E '^api_restart_baseline=' "${STATE_FILE}" 2>/dev/null | head -n1 | cut -d= -f2- | tr -cd '0-9' || true)"
  STATE_LAST="$(grep -E '^last_state=' "${STATE_FILE}" 2>/dev/null | head -n1 | cut -d= -f2- || true)"
}

save_state() {
  local content tmp
  content="$(printf 'api_container_id=%s\napi_restart_baseline=%s\nlast_state=%s\n' \
    "${STATE_API_ID}" "${STATE_RESTART_BASELINE}" "${STATE_LAST}")"

  # Solo escribe si cambio (evita I/O innecesario cada ejecucion).
  if [[ -f "${STATE_FILE}" ]] && [[ "$(cat "${STATE_FILE}" 2>/dev/null || true)" == "${content}" ]]; then
    return 0
  fi

  if ! tmp="$(mktemp "${STATE_FILE}.tmp.XXXXXX" 2>/dev/null)"; then
    return 1
  fi
  printf '%s' "${content}" >"${tmp}"
  chmod 600 "${tmp}"
  # mv atomico SOLO sobre el fichero de estado propio del monitor.
  mv -f "${tmp}" "${STATE_FILE}" 2>/dev/null
  chmod 600 "${STATE_FILE}" 2>/dev/null || true
  rm -f "${tmp}" 2>/dev/null || true
}

cleanup() {
  # Solo elimina temporales propios que hayan podido quedar de una
  # interrupcion. Nunca toca backups/logs/produccion.
  rm -f "${STATE_FILE}.tmp."* 2>/dev/null || true
}
trap cleanup EXIT

# ---- Main -------------------------------------------------------------
main() {
  local start_ts now
  local local_up public_up docker_up
  local api_status api_health api_restarts api_id api_info
  local pg_status pg_health pg_info
  local cf_status cf_restarts cf_info
  local restart_class delta
  local disk_use inode_use
  local backup_newest backup_mtime backup_size backup_age_h
  local latest_line est_estado est_short
  local prev_id prev_base
  local code overall dur

  start_ts="$(date +%s)"
  local_up=0
  public_up=0
  docker_up=0

  load_state

  # ---- Docker daemon ---------------------------------------------------
  if docker info >/dev/null 2>&1; then
    docker_up=1
    add_result OK docker-daemon "docker responde"
  else
    add_result CRITICAL docker-daemon "docker daemon inaccesible"
  fi

  # ---- 1. API local -----------------------------------------------------
  if check_http "${LOCAL_HEALTH_URL}" "${HTTP_RETRIES}" "${HTTP_TIMEOUT_LOCAL}" "${HTTP_DELAY_LOCAL}"; then
    local_up=1
    add_result OK api-local "HTTP 200 + JSON status UP"
  else
    add_result CRITICAL api-local "no responde UP tras ${HTTP_RETRIES} intentos"
  fi

  # ---- 2. API publica ----------------------------------------------------
  # Si local DOWN y publica DOWN: el CRITICAL de api-local ya existe; aqui
  # se confirma que la caida es total (se mantiene CRITICAL, informacion
  # real, no ruido). Si local UP y publica DOWN: tunel/CF/DNS externo.
  if check_http "${PUBLIC_HEALTH_URL}" "${HTTP_RETRIES}" "${HTTP_TIMEOUT_PUBLIC}" "${HTTP_DELAY_PUBLIC}"; then
    public_up=1
    add_result OK api-publica "HTTP 200 + JSON status UP"
  else
    if [[ "${local_up}" -eq 1 ]]; then
      add_result WARNING api-publica "DOWN con API local UP (probable tunel/Cloudflare/DNS)"
    else
      add_result CRITICAL api-publica "DOWN y API local tambien DOWN (caida total)"
    fi
  fi

  # ---- 3. tareas-api (docker inspect estructurado) ------------------------
  api_info="$(docker inspect tareas-api --format '{{.State.Status}}|{{.State.Health.Status}}|{{.RestartCount}}|{{.Id}}' 2>/dev/null || true)"
  api_status=""; api_health=""; api_restarts=""; api_id=""
  IFS='|' read -r api_status api_health api_restarts api_id <<< "${api_info:-}" || true

  if [[ -z "${api_status}" ]]; then
    add_result CRITICAL tareas-api "docker inspect sin datos (contenedor ausente o daemon caido)"
  elif [[ "${api_status}" == "running" ]]; then
    case "${api_health}" in
      healthy)
        add_result OK tareas-api "running + healthy (restarts=${api_restarts:-0})"
        ;;
      starting)
        add_result WARNING tareas-api "running + health=starting (transitorio)"
        ;;
      unhealthy)
        add_result CRITICAL tareas-api "running + health=unhealthy"
        ;;
      *)
        add_result WARNING tareas-api "running + health no reportado"
        ;;
    esac
  else
    add_result CRITICAL tareas-api "State.Status=${api_status}"
  fi

  # ---- 4. Restart count (con baseline por container ID) --------------------
  # Un deploy normal crea un container ID nuevo -> baseline reset, no alerta.
  prev_id="${STATE_API_ID:-}"
  prev_base="${STATE_RESTART_BASELINE:-0}"
  restart_class="$(classify_restart "${prev_id}" "${prev_base}" "${api_id}" "${api_restarts}")"

  case "${restart_class}" in
    RESET)
      STATE_API_ID="${api_id}"
      STATE_RESTART_BASELINE="${api_restarts:-0}"
      add_result OK restart-count "container ID nuevo, baseline reset (total=${api_restarts:-0})"
      ;;
    OK)
      add_result OK restart-count "sin reinicios nuevos (total=${api_restarts:-0})"
      ;;
    WARN)
      delta=$(( api_restarts - prev_base ))
      add_result WARNING restart-count "reinicios desde baseline: +${delta}"
      STATE_RESTART_BASELINE="${api_restarts}"
      ;;
    CRIT)
      delta=$(( api_restarts - prev_base ))
      add_result CRITICAL restart-count "posible crash-loop: +${delta} reinicios desde baseline"
      STATE_RESTART_BASELINE="${api_restarts}"
      ;;
    *)
      add_result WARNING restart-count "sin datos para evaluar reinicios"
      ;;
  esac

  # ---- 5. tareas-postgres --------------------------------------------------
  pg_info="$(docker inspect tareas-postgres --format '{{.State.Status}}|{{.State.Health.Status}}' 2>/dev/null || true)"
  pg_status=""; pg_health=""
  IFS='|' read -r pg_status pg_health <<< "${pg_info:-}" || true

  if [[ -z "${pg_status}" ]]; then
    add_result CRITICAL postgres "docker inspect sin datos (contenedor ausente o daemon caido)"
  elif [[ "${pg_status}" == "running" ]]; then
    case "${pg_health}" in
      healthy)
        add_result OK postgres "running + healthy"
        ;;
      starting)
        add_result WARNING postgres "running + health=starting (transitorio)"
        ;;
      unhealthy)
        add_result CRITICAL postgres "running + health=unhealthy"
        ;;
      *)
        add_result WARNING postgres "running + health no reportado"
        ;;
    esac
  else
    add_result CRITICAL postgres "State.Status=${pg_status}"
  fi

  # ---- 6. cloudflared ------------------------------------------------------
  # Estado funcional real del tunel = check api-publica. Aqui solo contenedor.
  cf_info="$(docker inspect cloudflared --format '{{.State.Status}}|{{.RestartCount}}' 2>/dev/null || true)"
  cf_status=""; cf_restarts=""
  IFS='|' read -r cf_status cf_restarts <<< "${cf_info:-}" || true

  if [[ -z "${cf_status}" ]]; then
    add_result CRITICAL cloudflared "docker inspect sin datos (contenedor ausente o daemon caido)"
  elif [[ "${cf_status}" == "running" ]]; then
    add_result OK cloudflared "running (restarts=${cf_restarts:-0})"
  else
    add_result CRITICAL cloudflared "State.Status=${cf_status}"
  fi

  # ---- 6b. cloudflared-ready --------------------------------------------------
  # /ready (loopback) = diagnostico local del tunel. api-publica = señal funcional
  # end-to-end. /ready por si solo NO se eleva a CRITICAL: requiere ademas caida
  # de api-publica con api-local UP (tunel/edge realmente degradado). Si api-local
  # tambien DOWN, el CRITICAL total ya lo aportan api-local/api-publica (regla E:
  # no duplicar ruido). Si el contenedor no corre, check cloudflared ya es CRITICAL.
  check_cloudflared_ready "${CLOUDFLARED_READY_RETRIES}" "${CLOUDFLARED_READY_TIMEOUT}" "${CLOUDFLARED_READY_DELAY}"
  if [[ "${cf_status}" != "running" ]]; then
    add_result OK cloudflared-ready "skip: contenedor no running o sin dato (cubierto por cloudflared)"
  elif [[ "${CF_READY_STATE}" == "ok" ]]; then
    add_result OK cloudflared-ready "readyConnections=${CF_READY_CONNS}"
  elif [[ "${CF_READY_STATE}" == "degraded" ]]; then
    if (( public_up == 1 )); then
      add_result WARNING cloudflared-ready "readyConnections=0 pero API pública UP"
    elif (( local_up == 1 )); then
      add_result CRITICAL cloudflared-ready "readyConnections=0 y API pública DOWN (tunel/edge degradado)"
    else
      add_result WARNING cloudflared-ready "readyConnections=0 y API pública DOWN con API local DOWN (ya CRITICAL en api-local)"
    fi
  else
    if (( public_up == 1 )); then
      add_result WARNING cloudflared-ready "/ready no disponible pero API pública UP"
    elif (( local_up == 1 )); then
      add_result CRITICAL cloudflared-ready "/ready no disponible y API pública DOWN (tunel/edge degradado)"
    else
      add_result WARNING cloudflared-ready "/ready no disponible y API pública DOWN con API local DOWN (ya CRITICAL en api-local)"
    fi
  fi

  # ---- 7. Timer CD ----------------------------------------------------------
  if [[ "$(systemctl is-active "${CD_TIMER}" 2>/dev/null || true)" == "active" ]]; then
    add_result OK timer-cd "${CD_TIMER} active"
  else
    add_result CRITICAL timer-cd "${CD_TIMER} no activo"
  fi

  # ---- 8. .failed-sha -------------------------------------------------------
  if [[ -s "${FAILED_SHA_FILE}" ]]; then
    est_short="$(head -c 12 "${FAILED_SHA_FILE}" 2>/dev/null || true)"
    add_result CRITICAL failed-sha "presente: ${est_short}..."
  else
    add_result OK failed-sha "inexistente o vacio"
  fi

  # ---- 9. Ultimo ESTADO del deploy (senal secundaria) -----------------------
  # Solo la ultima linea ESTADO= cuenta; un ESTADO=0 posterior anula un 3 viejo.
  # El service oneshot del poller esta normalmente inactive entre polls: no se
  # trata como fallo. .failed-sha tiene prioridad sobre este check.
  est_estado="$(grep -oE 'ESTADO=[0-9]' "${CD_LOG_FILE}" 2>/dev/null | tail -n1 | cut -d= -f2 || true)"
  case "${est_estado}" in
    0)
      add_result OK cd-deploy "ultimo ESTADO=0 (deploy OK)"
      ;;
    3)
      add_result WARNING cd-deploy "ultimo ESTADO=3 (deploy fallo, rollback OK)"
      ;;
    4)
      add_result CRITICAL cd-deploy "ultimo ESTADO=4 (rollback fallido)"
      ;;
    "")
      add_result OK cd-deploy "sin ESTADO previo (poller NOOP o log inexistente)"
      ;;
    *)
      add_result WARNING cd-deploy "ultimo ESTADO=${est_estado}"
      ;;
  esac

  # ---- 10. Timer backup (systemd user de mar) --------------------------------
  if [[ "$(systemctl --user is-active "${BACKUP_TIMER_USER}" 2>/dev/null || true)" == "active" ]]; then
    add_result OK timer-backup "${BACKUP_TIMER_USER} active (user)"
  else
    add_result CRITICAL timer-backup "${BACKUP_TIMER_USER} no activo (user systemd)"
  fi

  # ---- 11. Servicios relevantes en failed --------------------------------------
  # Se vigilan SOLO unidades relevantes (CD + backup + docker), no todo el host
  # (evita ruido de fwupd y otras units ajenas).
  if [[ "$(systemctl is-failed "${CD_SERVICE}" 2>/dev/null || true)" == "failed" ]]; then
    add_result WARNING systemd "${CD_SERVICE} en failed (poller; no derriba la app)"
  else
    add_result OK systemd "${CD_SERVICE} no failed"
  fi
  if [[ "$(systemctl --user is-failed "${BACKUP_SERVICE_USER}" 2>/dev/null || true)" == "failed" ]]; then
    add_result WARNING systemd "${BACKUP_SERVICE_USER} en failed"
  else
    add_result OK systemd "${BACKUP_SERVICE_USER} no failed"
  fi
  if [[ "$(systemctl is-failed docker.service 2>/dev/null || true)" == "failed" ]]; then
    add_result CRITICAL systemd "docker.service en failed"
  elif [[ "${docker_up}" -eq 1 ]]; then
    add_result OK systemd "docker.service no failed"
  else
    add_result OK systemd "docker.service no failed (daemon inaccesible)"
  fi

  # ---- 12. Backup freshness (mtime, no el nombre) ------------------------------
  backup_newest=""
  backup_mtime=0
  for f in "${BACKUP_DIR}"/tareas-app-*.dump; do
    if [[ -f "${f}" ]]; then
      m="$(stat -c %Y "${f}" 2>/dev/null || echo 0)"
      if (( m > backup_mtime )); then
        backup_mtime="${m}"
        backup_newest="${f}"
      fi
    fi
  done

  now="$(date +%s)"
  if [[ -z "${backup_newest}" ]]; then
    add_result CRITICAL backup-age "sin dumps en ${BACKUP_DIR}"
  else
    backup_age_h=$(( (now - backup_mtime) / 3600 ))
    if (( backup_age_h > BACKUP_CRIT_HOURS )); then
      add_result CRITICAL backup-age "${backup_age_h}h > ${BACKUP_CRIT_HOURS}h (${backup_newest})"
    elif (( backup_age_h > BACKUP_WARN_HOURS )); then
      add_result WARNING backup-age "${backup_age_h}h > ${BACKUP_WARN_HOURS}h (${backup_newest})"
    else
      add_result OK backup-age "${backup_age_h}h (${backup_newest})"
    fi
  fi

  # ---- 13. Backup size (dump ya validado por backup-db.sh con pg_restore) ------
  # El monitor NO reejecuta pg_restore; solo comprueba fichero regular y tamano.
  if [[ -z "${backup_newest}" ]]; then
    add_result CRITICAL backup-size "sin dump que comprobar"
  else
    backup_size="$(stat -c %s "${backup_newest}" 2>/dev/null || echo 0)"
    if [[ -f "${backup_newest}" ]] && (( backup_size >= BACKUP_MIN_BYTES )); then
      add_result OK backup-size "${backup_size}B >= ${BACKUP_MIN_BYTES}B (${backup_newest})"
    else
      add_result CRITICAL backup-size "${backup_size}B < ${BACKUP_MIN_BYTES}B o no regular (${backup_newest})"
    fi
  fi

  # ---- 14. Disco ----------------------------------------------------------------
  # df puede devolver "-" en filesystems sin soporte de ciertas columnas;
  # se valida que el valor sea numerico antes de comparar.
  disk_use="$(df -P / 2>/dev/null | awk 'NR==2 {gsub(/%/,"",$5); print $5}')"
  if [[ ! "${disk_use}" =~ ^[0-9]+$ ]]; then
    if [[ -z "${disk_use}" ]]; then
      add_result WARNING disco "no se pudo leer df /"
    else
      add_result OK disco "sin dato numerico (${disk_use})"
    fi
  elif (( disk_use >= DISK_CRIT )); then
    add_result CRITICAL disco "${disk_use}% >= ${DISK_CRIT}% (/)"
  elif (( disk_use >= DISK_WARN )); then
    add_result WARNING disco "${disk_use}% >= ${DISK_WARN}% (/)"
  else
    add_result OK disco "${disk_use}% (/)"
  fi

  # ---- 15. Inodos ----------------------------------------------------------------
  inode_use="$(df -iP / 2>/dev/null | awk 'NR==2 {gsub(/%/,"",$5); print $5}')"
  if [[ ! "${inode_use}" =~ ^[0-9]+$ ]]; then
    if [[ -z "${inode_use}" ]]; then
      add_result WARNING inodos "no se pudo leer df -i /"
    else
      add_result OK inodos "sin dato numerico (${inode_use})"
    fi
  elif (( inode_use >= INODE_CRIT )); then
    add_result CRITICAL inodos "${inode_use}% >= ${INODE_CRIT}% (/)"
  elif (( inode_use >= INODE_WARN )); then
    add_result WARNING inodos "${inode_use}% >= ${INODE_WARN}% (/)"
  else
    add_result OK inodos "${inode_use}% (/)"
  fi

  # ---- Resultado agregado ------------------------------------------------------
  if (( N_CRIT > 0 )); then
    code=2
    overall="CRITICAL"
  elif (( N_WARN > 0 )); then
    code=1
    overall="WARNING"
  else
    code=0
    overall="OK"
  fi
  STATE_LAST="${overall}"
  save_state

  dur="$(awk -v s="${start_ts}" -v n="$(date +%s)" 'BEGIN{printf "%.1f", n-s}')"
  printf '[monitor-health] RESULT %s OK=%d WARN=%d CRIT=%d -> exit %d (%ss)\n' \
    "${overall}" "${N_OK}" "${N_WARN}" "${N_CRIT}" "${code}" "${dur}"

  exit "${code}"
}

main "$@"
