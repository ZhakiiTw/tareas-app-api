# Tareas App API

API REST desarrollada con Java y Spring Boot para la gestión de tareas.

Está pensada para ser consumida desde una aplicación Android y otros clientes frontend.

---

## Tecnologías utilizadas

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- PostgreSQL
- H2 Database
- Swagger / OpenAPI
- Spring Boot Actuator
- Lombok
- Gradle
- Docker
- Docker Compose

---

## Funcionalidades principales

- Registro e inicio de sesión de usuarios
- Autenticación mediante JWT
- Gestión de tareas
- Gestión de tipos de tarea
- Validación de datos
- Documentación de endpoints con Swagger
- Persistencia de datos con JPA
- Configuración separada para desarrollo y producción
- Despliegue mediante Docker Compose
- Healthcheck mediante Spring Boot Actuator
- Copias de seguridad de PostgreSQL

---

## Ejecución local sin Docker

### 1. Clonar el repositorio

```bash
git clone https://github.com/ZhakiiTw/tareas-app-api.git
```

### 2. Entrar en el proyecto

```bash
cd tareas-app-api
```

### 3. Ejecutar la aplicación

En Linux o macOS:

```bash
./gradlew bootRun
```

En Windows:

```powershell
.\gradlew.bat bootRun
```

---

## Swagger

Cuando Swagger está habilitado y la API se encuentra iniciada:

```text
http://localhost:8080/swagger-ui/index.html
```

Swagger puede habilitarse o deshabilitarse mediante una variable de entorno.

---

## Endpoints principales

### Autenticación

```http
POST /auth/registro
POST /auth/login
```

### Tareas

```http
GET /tareas
POST /tareas
PATCH /tareas/{id}
PATCH /tareas/{id}/completar
PATCH /tareas/{id}/reabrir
DELETE /tareas/{id}
```

### Tipos de tarea

```http
GET /tipos-tarea
POST /tipos-tarea
PATCH /tipos-tarea/{id}
DELETE /tipos-tarea/{id}
```

### Usuario autenticado

```http
GET /usuarios/me
```

---

## Seguridad

La API utiliza autenticación mediante JWT.

El token debe enviarse en la cabecera de las peticiones protegidas:

```http
Authorization: Bearer <token>
```

No deben subirse al repositorio contraseñas, secretos JWT ni archivos `.env`.

---

## Bases de datos

La aplicación puede utilizar:

- H2 para desarrollo
- PostgreSQL para producción

En el despliegue Docker, PostgreSQL se ejecuta en un contenedor independiente y no publica su puerto `5432` hacia el host.

---

# Despliegue con Docker

## Requisitos

- Git
- Docker Engine o Docker Desktop
- Docker Compose v2, disponible mediante `docker compose`

En un servidor Ubuntu solo es necesario instalar Git y Docker. Java y PostgreSQL se ejecutan dentro de los contenedores.

---

## Preparar las variables de entorno

El repositorio incluye el archivo `.env.example` como plantilla.

Crea el archivo real:

```bash
cp .env.example .env
```

El archivo `.env` está ignorado por Git y no debe subirse al repositorio.

Genera una contraseña segura para PostgreSQL:

```bash
openssl rand -base64 32
```

Genera un secreto JWT en Base64:

```bash
openssl rand -base64 64
```

Edita el archivo:

```bash
nano .env
```

Sustituye todos los valores que comiencen por:

```text
CAMBIAR_POR_
```

Para un primer despliegue dentro de una red local pueden mantenerse valores similares a estos:

```dotenv
API_PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:8080
SWAGGER_ENABLED=true
```

El puerto `8080` publicado en el servidor es una configuración inicial. En producción, la API debería situarse detrás de un reverse proxy con HTTPS.

---

## Validar la configuración

Antes de levantar los contenedores:

```bash
docker compose config
```

Este comando permite detectar variables ausentes o errores de sintaxis en `compose.yaml`.

---

## Construir y levantar los servicios

```bash
docker compose up -d --build
```

También se puede utilizar:

```bash
make deploy
```

Docker Compose levantará:

- La API Spring Boot con el perfil `prod`
- PostgreSQL
- La red interna entre ambos servicios
- El volumen persistente de la base de datos

---

## Comprobar el estado

```bash
docker compose ps
```

Consultar el healthcheck de la API:

```bash
curl -fsS http://localhost:${API_PORT:-8080}/actuator/health
```

También puede utilizarse:

```bash
make status
```

Una respuesta correcta será similar a:

```json
{
  "status": "UP"
}
```

El endpoint público de salud no muestra detalles internos de la aplicación.

---

## Consultar logs

```bash
docker compose logs -f api
```

O mediante:

```bash
make logs
```

En producción, la aplicación escribe los logs en la salida estándar del contenedor. Docker se encarga de la rotación configurada en Compose.

---

## Detener los servicios

```bash
docker compose down
```

O:

```bash
make down
```

No utilices:

```bash
docker compose down -v
```

salvo que quieras eliminar también el volumen persistente y todos los datos de PostgreSQL.

---

## Actualizar la aplicación desde Git

```bash
git pull
docker compose up -d --build
docker compose ps
```

También puede utilizarse:

```bash
git pull
make deploy
```

---

## Backup de PostgreSQL

Ejecuta:

```bash
make backup
```

Los archivos se guardan dentro del directorio:

```text
backups/
```

Cada backup incluye la fecha y la hora en su nombre. El script no elimina automáticamente los backups anteriores.

---

## Restaurar PostgreSQL

Para evitar escrituras durante la restauración, detén primero la API:

```bash
docker compose stop api
```

Restaura el archivo deseado:

```bash
gunzip -c backups/tareas-postgres-YYYYMMDD-HHMMSS.sql.gz | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Vuelve a levantar la API:

```bash
docker compose up -d api
```

Comprueba finalmente el estado:

```bash
make status
```

---

## Estructura relacionada con el despliegue

```text
tareas-app-api/
├── Dockerfile
├── compose.yaml
├── .dockerignore
├── .env.example
├── Makefile
├── scripts/
│   ├── deploy.sh
│   ├── logs.sh
│   ├── status.sh
│   └── backup-db.sh
├── src/
├── build.gradle
└── README.md
```

---

## Autor

Marcos Fernández

---

## Estado del proyecto

Proyecto en desarrollo.