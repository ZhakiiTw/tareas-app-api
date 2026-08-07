# CD — Continuous Deployment de tareas-app-api

> Este documento describe el flujo automatizado. **En este PR el timer del
> poller NO está activado**: los scripts están versionados y listos, pero el
> servidor no ejecuta ningún despliegue automático todavía.

## Flujo automático (cuando el timer esté activado)

```
merge a master (PR + security-scan verde)
  -> GitHub Actions (deploy.yml) construye y publica:
       ghcr.io/marfern2/tareas-app-api:<sha12>
       ghcr.io/marfern2/tareas-app-api:master   (solo push a master)
  -> servidor: cd-poll.sh (timer) detecta SHA nuevo en origin/master
       -> cd-deploy.sh <sha> despliega SOLO el servicio api
```

`cd-deploy.sh` nunca ejecuta `docker compose down` y nunca toca
`postgres`, `cloudflared` ni volúmenes.

## Estados

| Archivo | Contenido | Significado |
|---|---|---|
| `.deployed-sha` | SHA completo | Último SHA desplegado con éxito |
| `.failed-sha` | SHA completo | SHA cuyo deploy falló; no se reintenta automáticamente |

## Exit codes de `cd-deploy.sh`

| Código | Significado |
|---|---|
| `0` | deploy correcto |
| `1` | error operacional antes de modificar el servicio (imagen inexistente, git, pull, lock ocupado) |
| `2` | uso inválido / SHA inválido |
| `3` | deploy falla pero el rollback funciona |
| `4` | deploy falla **y** el rollback falla (CRÍTICO) |

Estados `3` y `4` escriben el SHA en `.failed-sha`. Un SHA fallido **no** se
reintenta automáticamente; un SHA nuevo sí se intenta con normalidad.

## Rollback manual

```bash
ssh marserver
cd /srv/docker/tareas-app-api

# Ver historial de backups de .env y el último SHA desplegado
ls -t backups-antes-deploy/.env-*.bak
cat .deployed-sha

# Volver a un SHA anterior ya publicado (imagen conservada localmente)
./scripts/cd-deploy.sh <sha-completo-40hex>

# Equivalente manual sin el script:
#   cp backups-antes-deploy/.env-<ts>.bak .env && chmod 600 .env
#   docker compose up -d --no-deps api
#   curl -fsS http://127.0.0.1:8080/actuator/health
```

## Reintento manual de un SHA fallido

```bash
cd /srv/docker/tareas-app-api
./scripts/cd-deploy.sh "$(cat .failed-sha)"
```

O, si se quiere volver a intentar el SHA actual de master:

```bash
./scripts/cd-deploy.sh "$(git rev-parse origin/master)"
```

## Logs

```bash
tail -n 50 /srv/docker/tareas-app-api/logs/cd-deploy.log
journalctl --user -u tareas-app-cd.service -n 50        # cuando exista el timer
```

## Sincronización de ficheros en el servidor

`cd-deploy.sh` sincroniza **solo** `compose.yaml` y `scripts/` desde
`origin/master` (mediante `git fetch` + `git archive`, sin `reset --hard` ni
`git clean`). Nunca toca `.env`, `logs/`, `backups/`, `backups-antes-deploy/`,
`.deployed-sha`, `.failed-sha` ni el resto del árbol.

## Requisito previo de GHCR

El package `ghcr.io/marfern2/tareas-app-api` debe ser **público** (el servidor
hace `docker manifest inspect` sin autenticación). Verificar tras el primer
push. Si fuera privado, el poller trataría la imagen como "aún no publicada"
indefinidamente.
