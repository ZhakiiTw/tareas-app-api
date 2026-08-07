#!/usr/bin/env bash
# Tests de scripts/cleanup-docker-images.sh usando un mock de `docker`.
# Cada escenario construye un fixture en un dir temporal y valida salida/exit.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CLEANUP="${ROOT}/scripts/cleanup-docker-images.sh"
MOCK_BIN="${SCRIPT_DIR}/mock"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

PASS=0
FAIL=0

check() {
  local desc="$1"
  shift
  if "$@"; then
    echo "  PASS: ${desc}"
    (( PASS++ ))
  else
    echo "  FAIL: ${desc}"
    (( FAIL++ ))
  fi
}

ncheck() {
  local desc="$1"
  shift
  if "$@"; then
    echo "  FAIL: ${desc}"
    (( FAIL++ ))
  else
    echo "  PASS: ${desc}"
    (( PASS++ ))
  fi
}

# ---------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------
F_303='303c13f6e2cb40d28d1846834209048f74898bc5f88e89d9c19293d1abd3dd9c'
F_E75='e75f3a93adb9a2a108d29e25c8efdee81c51d9468a3f5c822b976833ec27fe06'
F_B96='b960a48401c76bd2c7d0baebbc924c8011d43001134e2360207e8e465bd79944'
F_396='396153df470a61636dd6b61f162b53e38f146cf3f033471412175c300bb60763'
F_182='1821634b02443f56a51e4a6f660713cf802417abe4ca3769ca25e3547960006b'
F_1D7='1d76999053b4e45509c7aeaf299dc511f2fffbbd31be9e099703648dde5397fa'
F_D2C='d2c9b6ded49584e4f665031ff7128f2ee021a746fe9c37c521c8250d92d39125'
F_CLD='4f6655284ab3d252b7f28fedb19fe6c8fc82ee5b1295c20ac74d475e5398a52d'
F_PG='742f40ea20b9ff2ff31db5458d127452988a2164df9e17441e191f3b72252193'
F_NGX='54f2a904c251d5a34adf545a72d32515a15e08418dae0266e23be2e18c66fefa'
F_HW='96498ffd522e70807ab6384a5c0485a79b9c7c08ca79ba08623edcad1054e62d'
F_API='186f2ec7176ad647218826fdd36a6c6cd0fe5b197e1b35201018eaa277cc8c2d'

REPO='ghcr.io/marfern2/tareas-app-api'

# basic: 7 backend (actual + 5 previas + sexta antigua) + 5 ajenas
build_basic() {
  local d="$1"
  mkdir -p "$d"
  {
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" '4116248bd284' "sha256:${F_303}" '2026-08-07 21:58:54 +0000 UTC' '769MB'
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" '884070cd00c1' "sha256:${F_E75}" '2026-08-07 21:33:58 +0000 UTC' '769MB'
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" 'defa07f8ece2' "sha256:${F_B96}" '2026-08-07 20:42:24 +0000 UTC' '769MB'
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" 'dafa850f28e6' "sha256:${F_396}" '2026-08-07 19:36:10 +0000 UTC' '769MB'
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" 'eda55d3a9aa3' "sha256:${F_182}" '2026-08-07 19:15:43 +0000 UTC' '769MB'
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" 'cccc116c62a3' "sha256:${F_1D7}" '2026-08-07 18:53:01 +0000 UTC' '769MB'
    printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" '903f84bcea4b' "sha256:${F_D2C}" '2026-08-07 14:59:10 +0000 UTC' '766MB'
    printf '%s\t%s\t%s\t%s\t%s\n' 'cloudflare/cloudflared' 'latest' "sha256:${F_CLD}" '2026-07-17 12:00:00 +0000 UTC' '96.1MB'
    printf '%s\t%s\t%s\t%s\t%s\n' 'postgres' '17-alpine' "sha256:${F_PG}" '2026-07-11 12:00:00 +0000 UTC' '424MB'
    printf '%s\t%s\t%s\t%s\t%s\n' 'nginx' 'alpine' "sha256:${F_NGX}" '2026-06-27 12:00:00 +0000 UTC' '93.3MB'
    printf '%s\t%s\t%s\t%s\t%s\n' 'hello-world' 'latest' "sha256:${F_HW}" '2026-04-01 12:00:00 +0000 UTC' '25.9kB'
    printf '%s\t%s\t%s\t%s\t%s\n' 'tareas-app-api-api' 'latest' "sha256:${F_API}" '2026-08-06 22:00:00 +0000 UTC' '766MB'
  } > "$d/images.txt"

  printf 'aaaa\nbbbb\ncccc\n' > "$d/ps-aq.txt"
  {
    printf '%s\n' "$REPO:4116248bd284"
    printf '%s\n' "cloudflare/cloudflared:2026.7.2@sha256:${F_CLD}"
    printf '%s\n' 'postgres:17-alpine'
  } > "$d/ps-image.txt"
  {
    printf '%s\t%s\n' 'aaaa' "sha256:${F_303}"
    printf '%s\t%s\n' 'bbbb' "sha256:${F_CLD}"
    printf '%s\t%s\n' 'cccc' "sha256:${F_PG}"
  } > "$d/cid2img.txt"
  {
    printf '%s\t%s:%s\n' "sha256:${F_303}" "$REPO" '4116248bd284'
    printf '%s\t%s:%s\n' "sha256:${F_E75}" "$REPO" '884070cd00c1'
    printf '%s\t%s:%s\n' "sha256:${F_B96}" "$REPO" 'defa07f8ece2'
    printf '%s\t%s:%s\n' "sha256:${F_396}" "$REPO" 'dafa850f28e6'
    printf '%s\t%s:%s\n' "sha256:${F_182}" "$REPO" 'eda55d3a9aa3'
    printf '%s\t%s:%s\n' "sha256:${F_1D7}" "$REPO" 'cccc116c62a3'
    printf '%s\t%s:%s\n' "sha256:${F_D2C}" "$REPO" '903f84bcea4b'
    printf '%s\t%s\n' "sha256:${F_CLD}" 'cloudflare/cloudflared:latest'
    printf '%s\t%s\n' "sha256:${F_PG}" 'postgres:17-alpine'
    printf '%s\t%s\n' "sha256:${F_NGX}" 'nginx:alpine'
    printf '%s\t%s\n' "sha256:${F_HW}" 'hello-world:latest'
    printf '%s\t%s\n' "sha256:${F_API}" 'tareas-app-api-api:latest'
  } > "$d/id2tags.txt"
  {
    printf '%s\n' 'REPOSITORY TAG IMAGE ID CREATED SIZE SHARED UNIQUE CONTAINERS'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" '4116248bd284' '303c13f6e2cb' '2026-08-07' '769MB' '488MB' '281.4MB' '1'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" '884070cd00c1' 'e75f3a93adb9' '2026-08-07' '769MB' '488MB' '281.4MB' '0'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" 'defa07f8ece2' 'b960a48401c7' '2026-08-07' '769MB' '488MB' '281.4MB' '0'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" 'dafa850f28e6' '396153df470a' '2026-08-07' '769MB' '488MB' '281.4MB' '0'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" 'eda55d3a9aa3' '1821634b0244' '2026-08-07' '769MB' '488MB' '281.4MB' '0'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" 'cccc116c62a3' '1d76999053b4' '2026-08-07' '769MB' '488MB' '281.4MB' '0'
    printf '%s %s %s %s %s %s %s %s\n' "$REPO" '903f84bcea4b' 'd2c9b6ded495' '2026-08-07' '766MB' '488MB' '278.1MB' '0'
  } > "$d/df-v.txt"

  printf '%s\n' '4116248bd28478ebd8bdd460e9ebc75841a326ed' > "$d/.deployed-sha"
  printf 'API_IMAGE_TAG=4116248bd284\n' > "$d/.env"
}

run_cleanup() {
  local dir="$1"
  shift
  local envs=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      *=*) envs+=("$1"); shift ;;
      *) break ;;
    esac
  done
  (
    cd "${WORK}" || exit 1
    env CLEANUP_PROJECT_DIR="${dir}" MOCK_DATA_DIR="${dir}" PATH="${MOCK_BIN}:${PATH}" \
      "${envs[@]}" bash "${CLEANUP}" "$@"
  ) 2>&1
}

# ---------------------------------------------------------------------
# Escenario A: actual + 5 anteriores + sexta antigua -> sexta CANDIDATE
# ---------------------------------------------------------------------
echo "== A: retencion basica (actual + 5)"
DIR_A="${WORK}/a"; build_basic "$DIR_A"
OUT_A="$(run_cleanup "$DIR_A")"; RC_A=$?
check "exit 0" test "$RC_A" -eq 0
check "actual PROTECTED" grep -q 'PROTECTED 303c13f6e2cb' <<< "$OUT_A"
check "actual razon desplegado" grep -q 'desplegado actual' <<< "$OUT_A"
check "5 KEEP" test "$(grep -c '^KEEP' <<< "$OUT_A")" -eq 5
check "sexta CANDIDATE" grep -q 'CANDIDATE d2c9b6ded495' <<< "$OUT_A"
check "sexta razon excede retencion" grep -q 'excede retencion' <<< "$OUT_A"
check "resumen Candidates: 1" grep -q 'Candidates: 1' <<< "$OUT_A"
check "resumen Keep: 5" grep -q 'Keep: 5' <<< "$OUT_A"
check "modo DRY-RUN" grep -q 'Mode: DRY-RUN' <<< "$OUT_A"
check "ninguna ajena borrable"
ncheck grep -q 'CANDIDATE 4f6655284ab3' <<< "$OUT_A"
check "nginx protegida" grep -q 'PROTECTED 54f2a904c251.*tag de otro repository' <<< "$OUT_A"
check "estimacion unica ~278.1MB" grep -q '278.1 MB' <<< "$OUT_A"

# ---------------------------------------------------------------------
# Escenario B: master + sha comparten IMAGE ID -> un unico ID PROTECTED
# ---------------------------------------------------------------------
echo "== B: master + sha mismo ID"
DIR_B="${WORK}/b"; build_basic "$DIR_B"
# anadir una imagen antigua cuyo ID comparte tag sha + master
printf '%s\t%s\t%s\t%s\t%s\n' "$REPO" 'master' "sha256:${F_396}" '2026-08-06 10:00:00 +0000 UTC' '769MB' >> "$DIR_B/images.txt"
printf '%s\t%s\n' "sha256:${F_396}" "$REPO:bbbbbbbbbbbb" >> "$DIR_B/id2tags.txt"
printf '%s\t%s\n' "sha256:${F_396}" "$REPO:master" >> "$DIR_B/id2tags.txt"
# el id 396153df470a pasa a tener 3 tags: dafa850f28e6, bbbbbbbbbbbb, master
printf '%s %s %s %s %s %s %s %s\n' "$REPO" 'bbbbbbbbbbbb' '396153df470a' '2026-08-06' '769MB' '488MB' '281.4MB' '0' >> "$DIR_B/df-v.txt"
OUT_B="$(run_cleanup "$DIR_B")"; RC_B=$?
check "exit 0" test "$RC_B" -eq 0
check "ID master+sha PROTECTED" grep -q 'PROTECTED 396153df470a.*tag master' <<< "$OUT_B"
ncheck "mismo ID nunca CANDIDATE" grep -q 'CANDIDATE 396153df470a' <<< "$OUT_B"
check "solo un ID para master+sha" test "$(grep -c 'PROTECTED 396153df470a' <<< "$OUT_B")" -eq 1

# ---------------------------------------------------------------------
# Escenario C: ID backend + tag de otro repo -> PROTECTED entero
# ---------------------------------------------------------------------
echo "== C: tag ajeno en ID backend"
DIR_C="${WORK}/c"; build_basic "$DIR_C"
printf '%s\t%s\t%s\t%s\t%s\n' 'otrorepo/miapi' 'v1' "sha256:${F_D2C}" '2026-08-07 12:00:00 +0000 UTC' '766MB' >> "$DIR_C/images.txt"
printf '%s\t%s\n' "sha256:${F_D2C}" 'otrorepo/miapi:v1' >> "$DIR_C/id2tags.txt"
OUT_C="$(run_cleanup "$DIR_C")"; RC_C=$?
check "exit 0" test "$RC_C" -eq 0
check "ID con tag ajeno PROTECTED" grep -q 'PROTECTED d2c9b6ded495.*tag de otro repository' <<< "$OUT_C"
ncheck "nunca CANDIDATE" grep -q 'CANDIDATE d2c9b6ded495' <<< "$OUT_C"

# ---------------------------------------------------------------------
# Escenario D: imagen usada por container STOPPED -> PROTECTED
# ---------------------------------------------------------------------
echo "== D: container stopped usando imagen antigua"
DIR_D="${WORK}/d"; build_basic "$DIR_D"
printf 'dddd\n' >> "$DIR_D/ps-aq.txt"
printf '%s\n' "$REPO:903f84bcea4b" >> "$DIR_D/ps-image.txt"
printf '%s\t%s\n' 'dddd' "sha256:${F_D2C}" >> "$DIR_D/cid2img.txt"
printf '%s %s %s %s %s %s %s %s\n' "$REPO" '903f84bcea4b' 'd2c9b6ded495' '2026-08-07' '766MB' '488MB' '278.1MB' '1' >> "$DIR_D/df-v.txt"
OUT_D="$(run_cleanup "$DIR_D")"; RC_D=$?
check "exit 0" test "$RC_D" -eq 0
check "antigua en uso PROTECTED" grep -q 'PROTECTED d2c9b6ded495.*en uso por contenedor' <<< "$OUT_D"
ncheck "ninguna CANDIDATE" grep -q '^CANDIDATE' <<< "$OUT_D"

# ---------------------------------------------------------------------
# Escenario E: deployed SHA invalido -> dry-run sigue, --apply aborta (3)
# ---------------------------------------------------------------------
echo "== E: .deployed-sha invalido"
DIR_E="${WORK}/e"; build_basic "$DIR_E"
printf 'no-un-sha-valido\n' > "$DIR_E/.deployed-sha"
OUT_E="$(run_cleanup "$DIR_E")"; RC_E=$?
check "dry-run exit 0" test "$RC_E" -eq 0
check "dry-run aviso" grep -q '.deployed-sha invalido' <<< "$OUT_E"
check "dry-run sigue clasificando" grep -q '^PROTECTED' <<< "$OUT_E"
OUT_E2="$(run_cleanup "$DIR_E" --apply)"; RC_E2=$?
check "--apply aborta exit 3" test "$RC_E2" -eq 3
check "--apply mensaje seguridad" grep -q 'seguridad insuficiente' <<< "$OUT_E2"

# ---------------------------------------------------------------------
# Escenario F: .failed-sha presente -> dry-run sigue, --apply aborta (3)
# ---------------------------------------------------------------------
echo "== F: .failed-sha presente"
DIR_F="${WORK}/f"; build_basic "$DIR_F"
printf '%s\n' 'deadbeefdeadbeefdeadbeefdeadbeefdeadbeef' > "$DIR_F/.failed-sha"
OUT_F="$(run_cleanup "$DIR_F")"; RC_F=$?
check "dry-run exit 0" test "$RC_F" -eq 0
check "dry-run aviso failed" grep -q '.failed-sha presente' <<< "$OUT_F"
OUT_F2="$(run_cleanup "$DIR_F" --apply)"; RC_F2=$?
check "--apply aborta exit 3" test "$RC_F2" -eq 3
check "--apply mensaje failed" grep -q '.failed-sha' <<< "$OUT_F2"

# ---------------------------------------------------------------------
# Escenario G: dangling <none>:<none> -> PROTECTED, nunca CANDIDATE
# ---------------------------------------------------------------------
echo "== G: dangling"
DIR_G="${WORK}/g"; build_basic "$DIR_G"
printf '%s\t%s\t%s\t%s\t%s\n' '<none>' '<none>' 'sha256:deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef' '2026-08-01 00:00:00 +0000 UTC' '500MB' >> "$DIR_G/images.txt"
printf '%s\t%s\n' 'sha256:deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef' '<none>:<none>' >> "$DIR_G/id2tags.txt"
OUT_G="$(run_cleanup "$DIR_G")"; RC_G=$?
check "exit 0" test "$RC_G" -eq 0
check "dangling PROTECTED" grep -q 'PROTECTED deadbeefdead.*dangling' <<< "$OUT_G"
ncheck "dangling nunca CANDIDATE" grep -q 'CANDIDATE deadbeefdead' <<< "$OUT_G"

# ---------------------------------------------------------------------
# Escenario H: deploy lock ocupado -> exit 4, no cleanup
# ---------------------------------------------------------------------
echo "== H: lock de deploy ocupado"
DIR_H="${WORK}/h"; build_basic "$DIR_H"
( exec 8>"$DIR_H/.cd-deploy.lock"; flock 8; sleep 20 ) &
HOLDER=$!
sleep 0.3
OUT_H="$(run_cleanup "$DIR_H" CLEANUP_LOCK_RETRIES=1)"; RC_H=$?
kill "$HOLDER" 2>/dev/null
wait "$HOLDER" 2>/dev/null
check "exit 4" test "$RC_H" -eq 4
check "mensaje deploy en curso" grep -q 'deploy en curso' <<< "$OUT_H"
ncheck "no clasifico nada" grep -q '^CANDIDATE' <<< "$OUT_H"

# ---------------------------------------------------------------------
# Escenario I: RETAIN_PREVIOUS=3 -> actual + 3 anteriores
# ---------------------------------------------------------------------
echo "== I: RETAIN_PREVIOUS=3"
DIR_I="${WORK}/i"; build_basic "$DIR_I"
OUT_I="$(run_cleanup "$DIR_I" RETAIN_PREVIOUS=3)"; RC_I=$?
check "exit 0" test "$RC_I" -eq 0
check "3 KEEP" test "$(grep -c '^KEEP' <<< "$OUT_I")" -eq 3
check "3 CANDIDATE" test "$(grep -c '^CANDIDATE' <<< "$OUT_I")" -eq 3
check "resumen Keep: 3" grep -q 'Keep: 3' <<< "$OUT_I"
check "resumen Candidates: 3" grep -q 'Candidates: 3' <<< "$OUT_I"

# ---------------------------------------------------------------------
# Escenario J: --apply borra solo la candidata exacta (mock, sin --force)
# ---------------------------------------------------------------------
echo "== J: --apply con mock"
DIR_J="${WORK}/j"; build_basic "$DIR_J"
OUT_J="$(run_cleanup "$DIR_J" --apply)"; RC_J=$?
check "exit 0" test "$RC_J" -eq 0
check "REMOVED candidata" grep -q 'REMOVED d2c9b6ded495.*903f84bcea4b' <<< "$OUT_J"
check "log rm solo candidata" test "$(wc -l < "$DIR_J/rm.log")" -eq 1
check "log rm tag exacto" grep -qx "$REPO:903f84bcea4b" "$DIR_J/rm.log"
ncheck "no borro actual" grep -q "$REPO:4116248bd284" "$DIR_J/rm.log"
ncheck "no borro ajenas" grep -q -E 'cloudflare|postgres|nginx|hello-world|tareas-app-api-api' "$DIR_J/rm.log"
check "resumen Removed 1" grep -q 'Removed: 1' <<< "$OUT_J"

# ---------------------------------------------------------------------
echo
echo "===== Resultados: PASS=${PASS} FAIL=${FAIL} ====="
[[ "$FAIL" -eq 0 ]] && exit 0
exit 1
