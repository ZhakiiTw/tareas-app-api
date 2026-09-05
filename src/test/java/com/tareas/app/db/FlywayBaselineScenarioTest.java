package com.tareas.app.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escenario PRODUCCION (sin Spring, directamente con Flyway + Testcontainers):
 *   - schema ya existente equivalente a V1 (lo que deja ddl-auto=update hoy)
 *   - SIN flyway_schema_history
 *   - baseline-on-migrate=true + baseline-version=1
 *
 * Debe demostrar que:
 *   - Flyway registra historia BASELINE v1;
 *   - NO ejecuta el CREATE TABLE de V1 (fallaria con "already exists");
 *   - NO pierde los datos existentes;
 *   - Hibernate validate pasaria (mismas tablas/columnas).
 */
@Testcontainers
class FlywayBaselineScenarioTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    @DisplayName("Schema existente sin historial: baseline v1 sin reejecutar V1 ni perder datos")
    void baselineSobreSchemaExistente() throws IOException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 1. Simular produccion: schema equivalente a V1 ya creado (ddl-auto=update) + datos
        jdbc.execute(readV1Sql());
        jdbc.update("INSERT INTO usuarios (username, email, password) VALUES (?, ?, ?)",
                "prodexistente", "prod@example.com", "hash");
        jdbc.update("INSERT INTO tipos_tarea (nombre, color, usuario_id) VALUES ('Existente', '#123456', 1)");

        // 2. Garantizar que NO existe flyway_schema_history (como en produccion hoy)
        List<String> historyTables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name='flyway_schema_history'",
                String.class);
        assertThat(historyTables).isEmpty();

        // 3. Flyway con la configuracion exacta del PR de transicion
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        flyway.migrate();

        // 4. Historia: BASELINE v1 + SQL V2 (el baseline salta V1 y aplica la nueva migracion)
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("version")).isEqualTo("1");
        assertThat(history.get(0).get("type")).isEqualTo("BASELINE");
        assertThat(history.get(0).get("success")).isEqualTo(true);
        assertThat(history.get(1).get("version")).isEqualTo("2");
        assertThat(history.get(1).get("type")).isEqualTo("SQL");
        assertThat(history.get(1).get("success")).isEqualTo(true);

        // 5. No quedan migraciones pendientes (V1 == baseline version, V2 ya aplicada)
        assertThat(flyway.info().pending()).isEmpty();

        // 6. Los datos NO se pierden
        Integer countUsuarios = jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE username='prodexistente'", Integer.class);
        assertThat(countUsuarios).isEqualTo(1);
        Integer countTipos = jdbc.queryForObject("SELECT count(*) FROM tipos_tarea", Integer.class);
        assertThat(countTipos).isEqualTo(1);

        // 7. Las tablas originales siguen existiendo una unica vez y V2 crea refresh_tokens.
        //    Si V1 se hubiera ejecutado, migrate() habria fallado por "relation already exists".
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name IN ('usuarios','tipos_tarea','tareas','refresh_tokens') "
                        + "ORDER BY table_name",
                String.class);
        assertThat(tables).containsExactly("refresh_tokens", "tareas", "tipos_tarea", "usuarios");
    }

    private String readV1Sql() throws IOException {
        try (InputStream in = FlywayBaselineScenarioTest.class
                .getResourceAsStream("/db/migration/V1__baseline_schema.sql")) {
            assertThat(in).as("V1__baseline_schema.sql debe estar en el classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
