# Cloudflare Tunnel — cloudflared

Configuración versionada del túnel Cloudflare que publica la API en
`https://donit-api.marfern.dev/`.

La configuración de producción vive en `/srv/docker/cloudflare-tunnel/` en
marserver. Este directorio es la fuente versionada de la misma.

## Servicio

- Servicio `cloudflared` corriendo en Docker con `network_mode: host`.
- `container_name: cloudflared`, `restart: unless-stopped`.
- Ingress remoto (gestionado desde Cloudflare Dashboard por token):
  `donit-api.marfern.dev -> http://localhost:8080`.

## Secretos

- `.env` **no se versiona**. Solo existe en el servidor de producción con el
  valor real de `TUNNEL_TOKEN`.
- El repositorio solo contiene `.env.example` con un placeholder.
- `TUNNEL_TOKEN` se inyecta al contenedor únicamente vía `environment`.

## Imagen

Imagen pinneada por **versión + digest** (reproducibilidad):

```
cloudflare/cloudflared:2026.7.2@sha256:4f6655284ab3d252b7f28fedb19fe6c8fc82ee5b1295c20ac74d475e5398a52d
```

**Nunca volver a usar `latest` como imagen del servicio.**

## Métricas

El servidor de métricas está restringido a loopback:

```
--metrics 127.0.0.1:20241
```

Endpoints disponibles localmente:

- `http://127.0.0.1:20241/ready` — estado del túnel (HTTP 200 + JSON `{"status":200,...}`)
- `http://127.0.0.1:20241/metrics` — métricas Prometheus
- `http://127.0.0.1:20241/healthcheck` — healthcheck simple (HTTP 200 + `OK`)

No se exponen métricas en ninguna interfaz externa.

## Validación local

Con un token dummy (solo parseo, sin exponer secretos):

```bash
TUNNEL_TOKEN=dummy docker compose -f infra/cloudflare-tunnel/compose.yaml config
```

## Actualización controlada

1. Identificar nueva versión y su digest:
   `docker manifest inspect cloudflare/cloudflared:latest`.
2. Revisar el changelog de la release.
3. Cambiar tag y digest en `compose.yaml` en una rama.
4. PR / revisión.
5. Validar en el servidor: `docker compose config`.
6. `docker pull cloudflare/cloudflared:<tag>@sha256:<digest>`.
7. Recrear solo cloudflared: `docker compose up -d cloudflared`.
8. Verificar `/ready` local y la API pública.
9. Rollback si falla (ver abajo).

## Rollback

- Conservar siempre la imagen anterior local y el compose anterior.
- Restaurar el compose con el tag/digest anterior.
- `docker compose up -d cloudflared`.
- Verificar `/ready` y la API pública.
- **No borrar imágenes durante la operación.**
