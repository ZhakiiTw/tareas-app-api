#!/usr/bin/env bash
# =====================================================================
# cleanup-docker-images.sh — Limpieza LOCAL y controlada de imagenes Docker
# del repositorio ghcr.io/marfern2/tareas-app-api en marserver.
#
# SOLO limpia imagenes del repositorio backend. No toca otros repositorios,
# no toca imagenes dangling (<none>:<none>), no toca build cache, no toca
# GHCR remoto, no toca contenedores, no ejecuta system prune ni image prune -a.
#
# Dry-run por defecto. Unico modo destructivo: --apply (explicito).
#
# Coordinacion con el CD:
#   Usa el mismo lock que cd-deploy.sh (/srv/docker/tareas-app-api/.cd-deploy.lock)
#   con flock. Si el lock del deploy esta ocupado, NO analiza/borra nada y sale
#   con codigo 4. Ademas usa su propio lock anti-paralelo
#   (.cleanup-docker-images.lock).
#
# Retencion:
#   imagen actual desplegada (PROTECTED) + RETAIN_PREVIOUS (=5) versiones
#   anteriores por IMAGE ID. Las anteriores a esa ventana son CANDIDATE.
#
# Exit codes:
#   0 = ejecucion correcta (dry-run o apply)
#   1 = error operativo
#   2 = uso invalido / argumento desconocido
#   3 = seguridad insuficiente / no se puede determinar con certeza
#   4 = lock ocupado / deploy en curso
# =====================================================================

set -uo pipefail

REPOSITORY="ghcr.io/marfern2/tareas-app-api"
PROJECT_DIR="${CLEANUP_PROJECT_DIR:-/srv/docker/tareas-app-api}"
DEPLOY_LOCK="${PROJECT_DIR}/.cd-deploy.lock"
SELF_LOCK="${PROJECT_DIR}/.cleanup-docker-images.lock"
DEPLOYED_SHA_FILE="${PROJECT_DIR}/.deployed-sha"
FAILED_SHA_FILE="${PROJECT_DIR}/.failed-sha"
ENV_FILE="${PROJECT_DIR}/.env"
LOG_DIR="${PROJECT_DIR}/logs"
LOG_FILE="${LOG_DIR}/cleanup-docker-images.log"
LOCK_RETRIES="${CLEANUP_LOCK_RETRIES:-5}"
RETAIN_PREVIOUS="${RETAIN_PREVIOUS:-5}"

MODE="DRY-RUN"
deployed_sha=""
deployed_short=""
deployed_id=""
api_tag_short=""
failed_sha=""

declare -a IMG_IDS IMG_CREATED IMG_SIZE IMG_TAGS
declare -a USED_IDS USED_SHORTS USED_REFS
declare -a CAT REASON SORTED

# ---------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------
usage() {
  echo "Uso: $0 [--apply]" >&2
  echo "  Sin argumentos        -> dry-run (solo informa, NO borra)" >&2
  echo "  --apply               -> borra SOLO imagenes candidatas del backend" >&2
  echo "  RETAIN_PREVIOUS       -> versiones anteriores a conservar (default 5)" >&2
  exit 2
}

size_to_mb() {
  local v="$1" num suffix
  num="${v//[^0-9.]/}"
  suffix="${v//[0-9.]/}"
  case "${suffix}" in
    GB) awk -v n="$num" 'BEGIN{printf "%.1f", n*1024}' ;;
    MB) printf '%s' "${num}" ;;
    KB) awk -v n="$num" 'BEGIN{printf "%.2f", n/1024}' ;;
    B)  awk -v n="$num" 'BEGIN{printf "%.4f", n/1048576}' ;;
    *)  printf '%s' "${num}" ;;
  esac
}

log_apply() { printf '%s %s\n' "$(date -Is)" "$*" >> "${LOG_FILE}"; }

index_of_id() {
  local needle="$1" i
  for i in "${!IMG_IDS[@]}"; do
    [[ "${IMG_IDS[$i]}" == "${needle}" ]] && { echo "$i"; return 0; }
  done
  return 1
}

sort_by_created_desc() {
  local items=("$@") tmp i line
  SORTED=()
  tmp="$(mktemp)"
  for i in "${items[@]}"; do
    printf '%s\t%s\n' "${IMG_CREATED[$i]}" "$i" >> "${tmp}"
  done
  while IFS=$'\t' read -r _ i; do
    SORTED+=("$i")
  done < <(sort -r -t $'\t' -k1,1 "${tmp}")
  rm -f "${tmp}"
}

# ---------------------------------------------------------------------
# Locks
# ---------------------------------------------------------------------
acquire_locks() {
  exec 9>"${SELF_LOCK}"
  if ! flock -n 9; then
    echo "ERROR: otro limpiador en curso (${SELF_LOCK})" >&2
    exit 4
  fi

  local attempt=0
  exec 8>"${DEPLOY_LOCK}"
  while :; do
    if flock -n 8; then
      break
    fi
    (( attempt++ ))
    if (( attempt >= LOCK_RETRIES )); then
      echo "ERROR: deploy en curso (.cd-deploy.lock ocupado); no se ejecuta limpieza" >&2
      exit 4
    fi
    echo "AVISO: deploy en curso; reintento ${attempt}/${LOCK_RETRIES}..." >&2
    sleep 2
  done
}

# ---------------------------------------------------------------------
# Estado del deploy (.deployed-sha / .failed-sha / API_IMAGE_TAG)
# ---------------------------------------------------------------------
validate_markers() {
  failed_sha="$(cat "${FAILED_SHA_FILE}" 2>/dev/null || true)"
  if [[ -n "${failed_sha}" ]]; then
    echo "AVISO: .failed-sha presente (deploy fallido pendiente)." >&2
    echo "       En modo --apply se aborta por seguridad." >&2
    if [[ "${MODE}" == "APPLY" ]]; then
      echo "ERROR: seguridad insuficiente (.failed-sha); se aborta --apply" >&2
      exit 3
    fi
  fi

  deployed_sha="$(cat "${DEPLOYED_SHA_FILE}" 2>/dev/null || true)"
  deployed_short=""
  if [[ "${deployed_sha}" =~ ^[0-9a-f]{40}$ ]]; then
    deployed_short="${deployed_sha:0:12}"
  else
    echo "AVISO: .deployed-sha invalido o ausente." >&2
    echo "       En modo --apply se aborta por seguridad." >&2
    if [[ "${MODE}" == "APPLY" ]]; then
      echo "ERROR: seguridad insuficiente (.deployed-sha); se aborta --apply" >&2
      exit 3
    fi
  fi

  # Solo se lee la linea API_IMAGE_TAG del .env. Nunca se imprime el .env.
  api_tag_short="$(sed -n 's/^API_IMAGE_TAG=//p' "${ENV_FILE}" 2>/dev/null | tail -n1 | tr -d '\r')"
  if [[ ! "${api_tag_short}" =~ ^[0-9a-f]{12}$ ]]; then
    api_tag_short=""
  fi
}

# ---------------------------------------------------------------------
# Inventario: contenedores y imagenes, agrupado por IMAGE ID
# ---------------------------------------------------------------------
get_used() {
  USED_IDS=(); USED_SHORTS=(); USED_REFS=()
  local cid id ref
  while IFS= read -r cid; do
    [[ -z "${cid}" ]] && continue
    id="$(docker inspect -f '{{.Image}}' "${cid}" 2>/dev/null)" || continue
    id="${id#sha256:}"
    USED_IDS+=("${id,,}")
    USED_SHORTS+=("${id:0:12}")
  done < <(docker ps -aq --no-trunc 2>/dev/null)

  while IFS= read -r ref; do
    [[ -z "${ref}" ]] && continue
    USED_REFS+=("${ref}")
  done < <(docker ps -a --no-trunc --format '{{.Image}}' 2>/dev/null)
}

load_inventory() {
  IMG_IDS=(); IMG_CREATED=(); IMG_SIZE=(); IMG_TAGS=()
  local line repo tag id created size idx
  while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    IFS=$'\t' read -r repo tag id created size <<< "${line}"
    id="${id#sha256:}"
    id="${id,,}"
    if idx="$(index_of_id "${id}")"; then
      IMG_TAGS[idx]="${IMG_TAGS[idx]},${repo}:${tag}"
    else
      IMG_IDS+=("${id}")
      IMG_CREATED+=("${created}")
      IMG_SIZE+=("${size}")
      IMG_TAGS+=("${repo}:${tag}")
    fi
  done < <(docker images --no-trunc --format '{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedAt}}\t{{.Size}}' 2>/dev/null)
}

# ---------------------------------------------------------------------
# Clasificacion PROTECTED / KEEP / CANDIDATE
# ---------------------------------------------------------------------
classify() {
  CAT=(); REASON=()
  local i id short tags created tag t
  local -a taglist
  local has_backend
  deployed_id=""

  for i in "${!IMG_IDS[@]}"; do
    id="${IMG_IDS[$i]}"
    short="${id:0:12}"
    tags="${IMG_TAGS[$i]}"
    created="${IMG_CREATED[$i]}"
    local dangling=0 foreign=0 master=0 deployed=0 api=0 used=0
    IFS=',' read -ra taglist <<< "${tags}"
    for t in "${taglist[@]}"; do
      [[ -z "${t}" ]] && continue
      if [[ "${t}" == "<none>:<none>" ]]; then dangling=1; continue; fi
      if [[ "${t}" != "${REPOSITORY}:"* ]]; then foreign=1; fi
      if [[ "${t}" == "${REPOSITORY}:master" ]]; then master=1; fi
      if [[ -n "${deployed_short}" && "${t}" == "${REPOSITORY}:${deployed_short}" ]]; then
        deployed=1; deployed_id="${id}"
      fi
      if [[ -n "${api_tag_short}" && "${t}" == "${REPOSITORY}:${api_tag_short}" ]]; then api=1; fi
    done
    for t in "${taglist[@]}"; do
      [[ -z "${t}" ]] && continue
      if [[ -n "${deployed_short}" && "${t}" == "${REPOSITORY}:${deployed_short}" ]]; then
        deployed=1; deployed_id="${id}"
      fi
      for r in "${USED_REFS[@]}"; do
        [[ "${t}" == "${r}" ]] && used=1
      done
    done
    for s in "${USED_SHORTS[@]}"; do
      [[ "${s}" == "${short}" ]] && used=1
    done
    for u in "${USED_IDS[@]}"; do
      [[ "${u}" == "${id}" ]] && used=1
    done

    CAT[$i]=""
    if (( dangling )); then
      CAT[$i]="PROTECTED"; REASON[$i]="fuera de alcance: dangling <none>:<none>"
    elif (( foreign )); then
      CAT[$i]="PROTECTED"; REASON[$i]="tag de otro repository"
    elif (( deployed )); then
      CAT[$i]="PROTECTED"; REASON[$i]="desplegado actual (.deployed-sha)"
    elif (( api )); then
      CAT[$i]="PROTECTED"; REASON[$i]="API_IMAGE_TAG del .env"
    elif (( used )); then
      CAT[$i]="PROTECTED"; REASON[$i]="en uso por contenedor"
    elif (( master )); then
      CAT[$i]="PROTECTED"; REASON[$i]="tag master"
    fi
  done

  # Retencion entre IDs 100% backend (sin dangling ni tags ajenos).
  # all_backend incluye tambien los ya PROTECTED (p.ej. el desplegado), para
  # poder identificar la "imagen actual" aunque no este en la lista de KEEP.
  local -a all_backend=()
  for i in "${!IMG_IDS[@]}"; do
    has_backend=0
    IFS=',' read -ra taglist <<< "${IMG_TAGS[$i]}"
    for t in "${taglist[@]}"; do
      [[ "${t}" == "${REPOSITORY}:"* ]] && has_backend=1
    done
    (( has_backend )) && all_backend+=("$i")
  done

  if (( ${#all_backend[@]} > 0 )); then
    sort_by_created_desc "${all_backend[@]}"
    local current_idx="${SORTED[0]}" idx n=0
    if [[ -n "${deployed_id}" ]]; then
      for idx in "${SORTED[@]}"; do
        if [[ "${IMG_IDS[$idx]}" == "${deployed_id}" ]]; then current_idx="${idx}"; break; fi
      done
    fi
    if [[ -z "${CAT[$current_idx]}" ]]; then
      CAT[$current_idx]="PROTECTED"
      REASON[$current_idx]="imagen actual (mas reciente)"
    fi
    for idx in "${SORTED[@]}"; do
      [[ "${idx}" == "${current_idx}" ]] && continue
      [[ -n "${CAT[$idx]}" ]] && continue
      (( n++ ))
      if (( n <= RETAIN_PREVIOUS )); then
        CAT[$idx]="KEEP"
        REASON[$idx]="anterior #${n}/${RETAIN_PREVIOUS}"
      else
        CAT[$idx]="CANDIDATE"
        REASON[$idx]="excede retencion (anterior #${n}/${RETAIN_PREVIOUS})"
      fi
    done
  fi
}

validate_deployed_mapping() {
  if [[ -z "${deployed_short}" ]]; then
    return 0
  fi

  if [[ -z "${deployed_id}" ]]; then
    echo "AVISO: .deployed-sha es valido pero no puede mapearse a una imagen local." >&2

    if [[ "${MODE}" == "APPLY" ]]; then
      echo "ERROR: seguridad insuficiente: imagen desplegada no identificable; se aborta --apply" >&2
      exit 3
    fi
  fi
}

# ---------------------------------------------------------------------
# Salida
# ---------------------------------------------------------------------
print_classification() {
  local -a all=()
  local i cat
  for i in "${!IMG_IDS[@]}"; do all+=("$i"); done
  sort_by_created_desc "${all[@]}"
  for cat in PROTECTED KEEP CANDIDATE; do
    for i in "${SORTED[@]}"; do
      [[ "${CAT[$i]}" == "${cat}" ]] || continue
      printf '%-9s %s [%s] %s %s\n' \
        "${cat}" "${IMG_IDS[$i]:0:12}" "${IMG_TAGS[$i]//,/, }" "${IMG_CREATED[$i]}" "${REASON[$i]}"
    done
  done
}

print_summary() {
  local p=0 k=0 c=0 i apparent=0
  local -a cidx=()

  for i in "${!CAT[@]}"; do
    case "${CAT[$i]}" in
      PROTECTED) (( p++ )) ;;
      KEEP)      (( k++ )) ;;
      CANDIDATE) (( c++ )) ;;
    esac
  done

  for i in "${!CAT[@]}"; do
    [[ "${CAT[$i]}" == "CANDIDATE" ]] && cidx+=("$i")
  done

  for i in "${cidx[@]}"; do
    apparent="$(
      awk \
        -v a="${apparent}" \
        -v s="$(size_to_mb "${IMG_SIZE[$i]}")" \
        'BEGIN { printf "%.1f", a + s }'
    )"
  done

  echo "===== Resumen ====="
  echo "Repository: ${REPOSITORY}"
  echo "Mode: ${MODE}"
  echo "RETAIN_PREVIOUS: ${RETAIN_PREVIOUS}"
  echo "Protected: ${p}"
  echo "Keep: ${k}"
  echo "Candidates: ${c}"
  echo "Tamano aparente candidatos (incluye capas compartidas): ~${apparent} MB"
  echo "Espacio real recuperable: no determinado por adelantado debido a capas compartidas."
  echo "Comprobar con 'docker system df' antes/despues de --apply."
}

# ---------------------------------------------------------------------
# Modo --apply
# ---------------------------------------------------------------------
verify_candidate() {
  local id="$1" short="${1:0:12}" t
  local s u
  for s in "${USED_SHORTS[@]}"; do
    [[ "${s}" == "${short}" ]] && { echo "ahora en uso por contenedor"; return 1; }
  done
  for u in "${USED_IDS[@]}"; do
    [[ "${u}" == "${id}" ]] && { echo "ahora en uso por contenedor"; return 1; }
  done

  local tags
  tags="$(docker inspect -f '{{range .RepoTags}}{{println .}}{{end}}' "${id}" 2>/dev/null)"
  if [[ -z "${tags}" ]]; then
    echo "ya no existe o sin tags"; return 1
  fi
  while IFS= read -r t; do
    [[ -z "${t}" ]] && continue
    if [[ "${t}" == "<none>:<none>" ]]; then
      echo "ahora dangling"
      return 1
    fi

    if [[ "${t}" != "${REPOSITORY}:"* ]]; then
      echo "ahora con tag de otro repository"
      return 1
    fi

    if [[ "${t}" == "${REPOSITORY}:master" ]]; then
      echo "ahora con tag master"
      return 1
    fi

    if [[ -n "${deployed_short}" && "${t}" == "${REPOSITORY}:${deployed_short}" ]]; then
      echo "ahora es la imagen desplegada actual"
      return 1
    fi
  done <<< "${tags}"
  printf '%s\n' "${tags}"
  return 0
}

apply_cleanup() {
  echo "===== APPLY: recalculando inventario desde cero ====="
  get_used
  load_inventory
  classify
  validate_deployed_mapping

  local f
  f="$(cat "${FAILED_SHA_FILE}" 2>/dev/null || true)"
  if [[ -n "${f}" ]]; then
    echo "ERROR: .failed-sha presente; se aborta --apply" >&2
    exit 3
  fi
  f="$(cat "${DEPLOYED_SHA_FILE}" 2>/dev/null || true)"
  if [[ ! "${f}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "ERROR: .deployed-sha invalido/ausente; se aborta --apply" >&2
    exit 3
  fi

  local -a candidates=()
  local i removed=0 errors=0 skipped=0
  for i in "${!CAT[@]}"; do
    [[ "${CAT[$i]}" == "CANDIDATE" ]] && candidates+=("$i")
  done
  if (( ${#candidates[@]} == 0 )); then
    echo "Nada que borrar."
    return 0
  fi

  mkdir -p "${LOG_DIR}"
  for i in "${candidates[@]}"; do
    local id short tags tag err
    id="${IMG_IDS[$i]}"
    short="${id:0:12}"
    echo "---- candidato ${short} ----"
    tags="$(verify_candidate "${id}")" || {
      echo "SKIP ${short}: ${tags}"
      (( skipped++ ))
      continue
    }
    while IFS= read -r tag; do
      [[ -z "${tag}" ]] && continue
      if err="$(docker image rm "${tag}" 2>&1)"; then
        echo "REMOVED ${short} ${tag}"
        log_apply "REMOVED ${short} ${tag}"
        (( removed++ ))
      else
        echo "ERROR_SIN_FORZAR ${short} ${tag}: ${err}"
        log_apply "ERROR ${short} ${tag}: ${err}"
        (( errors++ ))
      fi
    done <<< "${tags}"
  done
  echo "===== APPLY resumen ====="
  echo "Removed: ${removed}  Errors: ${errors}  Skipped: ${skipped}"
}

# ---------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------
main() {
  if [[ "${1:-}" == "--apply" ]]; then
    MODE="APPLY"
  elif [[ -n "${1:-}" ]]; then
    echo "ERROR: argumento desconocido '${1}'" >&2
    usage
  fi
  if [[ ! "${RETAIN_PREVIOUS}" =~ ^[0-9]+$ ]]; then
    echo "ERROR: RETAIN_PREVIOUS debe ser un entero >= 0" >&2
    exit 2
  fi

  if ! docker version >/dev/null 2>&1; then
    echo "ERROR: docker no disponible" >&2
    exit 1
  fi
  if [[ ! -d "${PROJECT_DIR}" ]]; then
    echo "ERROR: PROJECT_DIR no existe: ${PROJECT_DIR}" >&2
    exit 1
  fi
  if [[ ! -w "${PROJECT_DIR}" ]]; then
    echo "ERROR: sin permisos de escritura en ${PROJECT_DIR} (usuario mar?)" >&2
    exit 1
  fi

  acquire_locks
  validate_markers

  get_used
  load_inventory
  classify
  validate_deployed_mapping
  print_classification

  if [[ "${MODE}" == "APPLY" ]]; then
    apply_cleanup
  fi
  print_summary
  exit 0
}

main "$@"
