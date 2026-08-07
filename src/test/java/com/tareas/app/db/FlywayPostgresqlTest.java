package com.tareas.app.db;

import com.tareas.app.model.Tarea;
import com.tareas.app.model.TipoTarea;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.TareaRepository;
import com.tareas.app.repository.TipoTareaRepository;
import com.tareas.app.repository.UsuarioRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Escenario objetivo: PostgreSQL VACIO
 *   -> Flyway aplica V1__baseline_schema.sql
 *   -> Hibernate validate (spring.jpa.hibernate.ddl-auto=validate)
 *   -> el contexto de Spring arranca y los repositorios funcionan.
 *
 * Usa Testcontainers con postgres:17-alpine (mismo tag que produccion).
 * No depende de H2 para validar el DDL de V1.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class FlywayPostgresqlTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TipoTareaRepository tipoTareaRepository;

    @Autowired
    private TareaRepository tareaRepository;

    @Test
    @DisplayName("Flyway ejecuta V1 en PostgreSQL vacio, validate pasa y los repositorios funcionan")
    void migracionDesdeCeroYFuncionamientoBasico() {
        // 1. flyway_schema_history contiene exactamente V1 (version 1, success=true)
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("version")).isEqualTo("1");
        assertThat(history.get(0).get("type")).isEqualTo("SQL");
        assertThat(history.get(0).get("success")).isEqualTo(true);

        // 2. Flyway informa V1 como aplicada y sin pendientes
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).anyMatch(m -> m.getVersion() != null && "1".equals(m.getVersion().getVersion()));
        assertThat(flyway.info().pending()).isEmpty();

        // 3. Las tres tablas existen (Hibernate validate ya ha arrancado el contexto)
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name IN ('usuarios','tipos_tarea','tareas') "
                        + "ORDER BY table_name",
                String.class);
        assertThat(tables).containsExactly("tareas", "tipos_tarea", "usuarios");

        // 4. Repositorios funcionan y la identity genera IDs
        Usuario alice = Usuario.builder()
                .username("alice").email("alice@example.com").password("hash1").build();
        Usuario savedAlice = usuarioRepository.save(alice);
        assertThat(savedAlice.getId()).isNotNull().isPositive();

        Usuario bob = Usuario.builder()
                .username("bob").email("bob@example.com").password("hash2").build();
        Usuario savedBob = usuarioRepository.save(bob);
        assertThat(savedBob.getId()).isEqualTo(savedAlice.getId() + 1);

        TipoTarea tipo = TipoTarea.builder()
                .nombre("Casa").descripcion("Tareas del hogar").color("#4F46E5").usuario(savedAlice).build();
        TipoTarea savedTipo = tipoTareaRepository.save(tipo);
        assertThat(savedTipo.getId()).isNotNull().isPositive();

        Tarea tarea = Tarea.builder()
                .titulo("Lavar la ropa")
                .descripcion("Ropa blanca y oscura")
                .fecha(LocalDate.now().plusDays(1))
                .completada(false)
                .urgencia(1)
                .usuario(savedAlice)
                .tipoTarea(savedTipo)
                .build();
        Tarea savedTarea = tareaRepository.save(tarea);
        assertThat(savedTarea.getId()).isNotNull().isPositive();
        assertThat(savedTarea.getTitulo()).isEqualTo("Lavar la ropa");

        assertThat(tareaRepository.findByUsuarioEmailOrderByFechaAsc("alice@example.com")).hasSize(1);
        assertThat(tipoTareaRepository.findByUsuarioEmailOrderByNombreAsc("alice@example.com")).hasSize(1);

        // 5. UNIQUE username se aplica a nivel de base de datos
        Usuario dupUsername = Usuario.builder()
                .username("alice").email("otra@example.com").password("hash3").build();
        assertThatThrownBy(() -> usuarioRepository.save(dupUsername))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 6. UNIQUE email se aplica a nivel de base de datos
        Usuario dupEmail = Usuario.builder()
                .username("otro").email("alice@example.com").password("hash4").build();
        assertThatThrownBy(() -> usuarioRepository.save(dupEmail))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 7. FK realmente funciona: tipo_tarea inexistente -> violacion de FK
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tareas (titulo, fecha, urgencia, tipo_tarea_id, usuario_id) "
                        + "VALUES ('fk', CURRENT_DATE, 0, 999999, ?)",
                savedAlice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 8. CHECK urgencia (0..2) realmente funciona: 99 fuera de rango -> violacion de CHECK
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tareas (titulo, fecha, urgencia, tipo_tarea_id, usuario_id) "
                        + "VALUES ('check', CURRENT_DATE, 99, ?, ?)",
                savedTipo.getId(), savedAlice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
