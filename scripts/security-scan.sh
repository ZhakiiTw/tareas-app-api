#!/usr/bin/env bash
# Escaneo reproducible de vulnerabilidades de dependencias.
#
# Ejecuta:
#   1. Generacion del SBOM CycloneDX (todas las dependencias directas y transitivas)
#   2. Actualizacion de la base NVD (dependencyCheckUpdate, requiere NVD_API_KEY)
#   3. OWASP Dependency-Check con autoUpdate=false (analiza la base NVD local)
#   4. OSV-Scanner via contenedor oficial ghcr.io/google/osv-scanner (fijado a v2.4.0)
#
# No modifica dependencias, no toca produccion y no imprime secretos.
# Si no hay NVD_API_KEY, Dependency-Check se marca como OMITIDO y el script devuelve
# codigo de error (INCOMPLETO) para que ningun CI se muestre verde con un escaneo
# incompleto. OSV-Scanner se ejecuta siempre.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DC_DIR="build/reports/dependency-check"
OSV_DIR="build/reports/osv-scanner"
SBOM="build/reports/cyclonedx-direct/bom.json"
OSV_VERSION="${OSV_SCANNER_VERSION:-v2.4.0}"

dc_state="PENDIENTE"
osv_state="OK"
fail=0

mkdir -p "$DC_DIR" "$OSV_DIR"

echo "==> [1/3] Generando SBOM CycloneDX"
./gradlew cyclonedxDirectBom --console=plain
[ -f "$SBOM" ] || { echo "ERROR: SBOM no generado en $SBOM"; exit 1; }

echo "==> [2/3] OWASP Dependency-Check"
if [ -z "${NVD_API_KEY:-}" ]; then
    dc_state="OMITIDO"
    echo "AVISO: NVD_API_KEY no esta definida. Con autoUpdate=false, Dependency-Check analiza"
    echo "       unicamente la base NVD local. Exporte NVD_API_KEY y ejecute una vez"
    echo "       './gradlew dependencyCheckUpdate' para descargarla, y luego relance este script"
    echo "       para completar el analisis."
elif ! ./gradlew dependencyCheckUpdate --console=plain 2>&1 | tee "$DC_DIR/dependency-check-update.log"; then
    dc_state="FALLIDO"
    echo "ERROR: la actualizacion de la base NVD fallo (dependencyCheckUpdate). No se analiza." >&2
    echo "       Detalle en $DC_DIR/dependency-check-update.log" >&2
    fail=1
elif ./gradlew dependencyCheckAnalyze --console=plain 2>&1 | tee "$DC_DIR/dependency-check.log"; then
    dc_state="OK"
    echo "OK: Dependency-Check sin vulnerabilidades >= CVSS 9.0"
else
    dc_state="FALLIDO"
    echo "ERROR: Dependency-Check fallo o reporto una vulnerabilidad critica (CVSS >= 9.0)." >&2
    echo "       Detalle en $DC_DIR/dependency-check.log" >&2
    fail=1
fi

echo "==> [3/3] OSV-Scanner (contenedor oficial ghcr.io/google/osv-scanner:${OSV_VERSION})"
if ! command -v docker >/dev/null 2>&1; then
    echo "ERROR: no se encuentra docker, necesario para OSV-Scanner." >&2
    exit 2
fi

set +e
docker run --rm \
    -v "$ROOT:/src" \
    "ghcr.io/google/osv-scanner:${OSV_VERSION}" \
    scan \
    --experimental-exclude=/src/build --experimental-exclude=/src/.gradle \
    --format=json --output-file="/src/$OSV_DIR/osv-results.json" \
    "/src/$SBOM"
osv_exit=$?
set -e

if [ ! -s "$OSV_DIR/osv-results.json" ]; then
    echo "ERROR: OSV-Scanner no produjo resultados." >&2
    exit 2
fi

if command -v python3 >/dev/null 2>&1; then
    osv_summary_file="$OSV_DIR/osv-summary.txt"
    python3 - "$OSV_DIR/osv-results.json" "$osv_summary_file" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    data = json.load(f)
sev_rank = {"CRITICAL": 4, "HIGH": 3, "MODERATE": 2, "LOW": 1}
max_sev = 0
max_label = "NONE"
vulns = []
for r in data.get("results", []):
    for p in r.get("packages", []):
        pkg = p.get("package", {})
        for v in p.get("vulnerabilities", []):
            sev = (v.get("database_specific") or {}).get("severity", "UNKNOWN").upper()
            rank = sev_rank.get(sev, 0)
            vulns.append((v.get("id"), pkg.get("name"), pkg.get("version"), sev))
            if rank > max_sev:
                max_sev, max_label = rank, sev
out = f"{len(vulns)} vulnerabilidad(es); severidad maxima = {max_label}\n"
out += "".join(f"  - {vid} {name}@{ver} [{sev}]\n" for vid, name, ver, sev in vulns)
with open(sys.argv[2], "w") as g:
    g.write(out)
sys.exit(1 if max_sev >= 3 else 0)
PY
    osv_rc=$?
    if [ "$osv_rc" -ne 0 ]; then
        osv_state="CRITICO"
        echo "ERROR: OSV-Scanner encontro vulnerabilidades CRITICAS/ALTAS confirmadas." >&2
        fail=1
    fi
    cat "$osv_summary_file"
else
    echo "AVISO: python3 no disponible; resumen de OSV basado en codigo de salida (exit=$osv_exit)."
    if [ "$osv_exit" -ne 0 ]; then
        osv_state="CRITICO"
        echo "ERROR: OSV-Scanner reporto vulnerabilidades (exit=$osv_exit)." >&2
        fail=1
    fi
fi

echo
echo "==> RESUMEN FINAL"
echo "  - SBOM CycloneDX          : OK"
echo "  - OWASP Dependency-Check  : $dc_state"
echo "  - OSV-Scanner             : $osv_state"
echo "  - Estado final            : $([ "$fail" -ne 0 ] && echo 'CON VULNERABILIDADES O FALLOS' || { [ "$dc_state" != "OK" ] && echo 'INCOMPLETO' || echo 'OK'; })"

if [ "$fail" -ne 0 ]; then
    echo "==> Escaneo finalizado con hallazgos o fallos. Revisar $DC_DIR y $OSV_DIR" >&2
    exit 1
fi
if [ "$dc_state" != "OK" ]; then
    echo "==> INCOMPLETO: Dependency-Check no se ejecuto correctamente (estado=$dc_state)." >&2
    echo "    Proporcione NVD_API_KEY y relance para un escaneo completo." >&2
    exit 3
fi
echo "==> Escaneo completo sin vulnerabilidades criticas."
