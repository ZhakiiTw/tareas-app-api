# syntax=docker/dockerfile:1.7

# Trazabilidad OCI: se inyecta en CI/despliegue mediante --build-arg BUILD_SHA.
ARG BUILD_SHA=unknown

FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon clean bootJar \
    && cp "$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -print -quit)" app.jar

FROM eclipse-temurin:21-jre

# ARG re-declarado en esta etapa para usarlo en LABEL
ARG BUILD_SHA=unknown

WORKDIR /app

LABEL org.opencontainers.image.source="https://github.com/marfern2/tareas-app-api"
LABEL org.opencontainers.image.revision="${BUILD_SHA}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build /workspace/app.jar /app/app.jar

RUN chown -R app:app /app

USER app

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS=""

ENTRYPOINT ["java", "-jar", "app.jar"]
