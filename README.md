# Tareas App API

API REST desarrollada con Java y Spring Boot para la gestión de tareas.  
Esta API está pensada para ser consumida desde un frontend de escritorio y una aplicación Android.

---

# Tecnologías utilizadas

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- JWT
- PostgreSQL
- H2 Database
- Swagger / OpenAPI
- Lombok
- Gradle

---

# Funcionalidades principales

- Registro e inicio de sesión de usuarios
- Autenticación mediante JWT
- Gestión de tareas
- Gestión de tipos de tarea
- Validación de datos
- Documentación de endpoints con Swagger
- Persistencia de datos con JPA

---

# Instalación y ejecución

## 1. Clonar el repositorio

```bash
git clone https://github.com/ZhakiiTw/tareas-app-api.git
```

## 2. Entrar en el proyecto

```bash
cd tareas-app-api
```

## 3. Ejecutar la aplicación

Linux / Mac:

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

---

# Swagger

Una vez iniciada la API:

```bash
http://localhost:8080/swagger-ui/index.html
```

---

# Endpoints principales

## Autenticación

```http
POST /auth/register
POST /auth/login
```

## Tareas

```http
GET /tareas
POST /tareas
PUT /tareas/{id}
DELETE /tareas/{id}
```

## Tipos de tarea

```http
GET /tipos-tarea
POST /tipos-tarea
PUT /tipos-tarea/{id}
DELETE /tipos-tarea/{id}
```

---

# Seguridad

La API utiliza autenticación JWT.

Enviar el token en la cabecera:

```http
Authorization: Bearer <token>
```

---

# Base de datos

Compatible con:

- PostgreSQL
- H2 Database

---

# Autor

Marcos Fernández

---

# Estado del proyecto

Proyecto en desarrollo.
