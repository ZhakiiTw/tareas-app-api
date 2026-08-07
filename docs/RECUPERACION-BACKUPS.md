# Recuperación de backups — PostgreSQL de tareas-app-api

> Sin secretos. Esta guía solo documenta; no modifica la base de producción.

## Resumen del sistema

| Elemento | Valor |
|---|---|
| Script | `/srv/docker/tareas-app-api/scripts/backup-db.sh` |
| Contenedor PostgreSQL | `tareas-postgres` (postgres:17-alpine) |
| Directorio de backups | `/srv/docker/backups/tareas-app-postgres/` |
| Formato | `pg_dump --format=custom` (`.dump`) |
| Nombre | `tareas-app-YYYYMMDD-HHMMSS.dump` |
| Permisos | directorio 700, archivos 600, usuario `mar` |
| Retención | 14 días, patrón exacto `tareas-app-*.dump` |
| Scheduler | systemd user timer `tareas-app-db-backup.timer` |
| Horario | diario 03:30 hora local del servidor (UTC), `Persistent=true`, `RandomizedDelaySec=10m` |
| Logs | journald de usuario (`journalctl --user`) |

## 1. Listar backups

```bash
ls -la /srv/docker/backups/tareas-app-postgres/
```

## 2. Verificar hash

```bash
sha256sum /srv/docker/backups/tareas-app-postgres/tareas-app-*.dump
```

El hash esperado se registra en el log de la ejecución que creó cada backup.

## 3. Probar el dump con pg_restore --list

El binario `pg_restore` no está en el host; se usa el del contenedor:

```bash
docker cp /srv/docker/backups/tareas-app-postgres/tareas-app-*.dump \
  tareas-postgres:/tmp/list-check.dump
docker exec tareas-postgres pg_restore --list /tmp/list-check.dump
docker exec tareas-postgres rm -f /tmp/list-check.dump
```

Debe listar tablas, secuencias y constraints sin errores.

## 4. Levantar una instancia temporal aislada

No expone puertos y no usa el volumen de producción:

```bash
docker run --rm -d \
  --name tareas-postgres-restore-test \
  --network none \
  -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  -e POSTGRES_USER=tareas_restore \
  -e POSTGRES_DB=tareas_restore_test \
  postgres:17-alpine
```

Esperar a que acepte conexiones:

```bash
for i in $(seq 1 40); do
  docker exec tareas-postgres-restore-test pg_isready -U tareas_restore -d tareas_restore_test >/dev/null 2>&1 && break
  sleep 1
done
docker exec tareas-postgres-restore-test pg_isready -U tareas_restore -d tareas_restore_test
```

## 5. Restaurar en una base nueva

```bash
docker cp /srv/docker/backups/tareas-app-postgres/tareas-app-*.dump \
  tareas-postgres-restore-test:/tmp/restore.dump
docker exec tareas-postgres-restore-test \
  pg_restore -U tareas_restore -d tareas_restore_test \
  --no-owner --no-privileges --exit-on-error /tmp/restore.dump
```

Comprobar solo conteos agregados (nunca imprimir datos personales):

```bash
printf "SELECT count(*) FROM tareas;\nSELECT count(*) FROM tipos_tarea;\nSELECT count(*) FROM usuarios;\n" \
  | docker exec -i tareas-postgres-restore-test \
      psql -U tareas_restore -d tareas_restore_test -tA
```

Eliminar el contenedor temporal al terminar:

```bash
docker rm -f tareas-postgres-restore-test
```

## 6. Cambiar la API a una base restaurada en una emergencia

Procedimiento general (documentación; no se ejecuta aquí sin autorización):

1. Restaurar el dump en un contenedor PostgreSQL nuevo con un volumen propio
   (pasos 4 y 5).
2. Editar `/srv/docker/tareas-app-api/.env` y actualizar las variables
   `DATABASE_*` (usar las credenciales del contenedor nuevo).
3. `docker compose up -d --build` para recrear el contenedor `tareas-api`.
4. Verificar: `curl -fsS http://127.0.0.1:8080/actuator/health` y
   `curl -fsS https://donit-api.marfern.dev/actuator/health`.
5. Probar primero en una instancia temporal antes de tocar producción.

## 7. Cómo volver atrás (rollback)

- El backup no modifica la base: es solo un archivo en disco. Para "volver",
  conserva el `.dump` y cualquier `.env` previo (ver `backups-antes-deploy/`).
- Si una restauración sobre una base nueva fallara, la base nueva se puede
  borrar sin afectar el volumen original `tareas-postgres-data`.
- El scheduler se puede pausar/reanudar sin borrar nada:

```bash
systemctl --user stop tareas-app-db-backup.timer   # pausa
systemctl --user start tareas-app-db-backup.timer  # reanuda
```

## 8. Qué nunca hacer sobre producción

- Nunca `pg_restore` directamente sobre el volumen/DB de producción.
- Nunca borrar el volumen `tareas-postgres-data`.
- Nunca `docker compose down` del servicio `postgres` ni detener
  `tareas-postgres` durante una restauración.
- Nunca borrar backups sin comprobar su antigüedad y que otro backup válido
  exista.

## 9. Cómo recuperar si el último backup falla

1. Comprobar el log: `journalctl --user -u tareas-app-db-backup.service -n 50`.
2. Buscar el fallo (suele ser `pg_dump fallo`, tamaño sospechoso o espacio).
3. Confirmar que el contenedor está healthy:
   `docker ps --filter name=tareas-postgres`.
4. Ejecutar un backup manual:
   `/srv/docker/tareas-app-api/scripts/backup-db.sh`.
5. Verificar el nuevo `.dump` (pasos 2 y 3) y probarlo en un contenedor
   temporal (pasos 4 y 5).
6. Si el error persiste, conservar el backup anterior: la retención solo
   elimina los que superan 14 días.

## 10. Comprobar espacio en disco

```bash
df -h /srv
du -sh /srv/docker/backups/tareas-app-postgres/
```

El script aborta si hay menos de 512 MB libres.

## Logs

```bash
journalctl --user -u tareas-app-db-backup.service -n 50 --no-pager
journalctl --user -u tareas-app-db-backup.service --since today
systemctl --user status tareas-app-db-backup.service
systemctl --user list-timers tareas-app-db-backup.timer
```

La rotación de journald es automática (no se usa logrotate aparte).

## Alertas (propuesta, no implementada)

- Webhook simple desde `OnFailure=`: un servicio auxiliar que haga un POST
  (p. ej. a un webhook de una app de chat) cuando
  `tareas-app-db-backup.service` marque `failed`.
- O un `ExecStartPost` en el servicio que, tras un `Result=failure`, envíe un
  correo vía `sendmail`.
- Consulta manual: `systemctl --user --failed`.
