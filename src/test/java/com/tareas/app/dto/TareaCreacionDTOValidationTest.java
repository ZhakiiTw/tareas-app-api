package com.tareas.app.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TareaCreacionDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private TareaCreacionDTO crearDtoConFecha(LocalDate fecha) {
        TareaCreacionDTO dto = new TareaCreacionDTO();
        dto.setTitulo("Tarea válida");
        dto.setFecha(fecha);
        dto.setTipoTareaId(1L);
        return dto;
    }

    @Test
    @DisplayName("Crear una tarea con fecha pasada es rechazado")
    void crearConFechaPasadaEsRechazado() {
        Set<ConstraintViolation<TareaCreacionDTO>> violations =
                validator.validate(crearDtoConFecha(LocalDate.now().minusDays(1)));

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("fecha"));
    }

    @Test
    @DisplayName("Crear una tarea con fecha actual es permitido")
    void crearConFechaActualEsPermitido() {
        Set<ConstraintViolation<TareaCreacionDTO>> violations =
                validator.validate(crearDtoConFecha(LocalDate.now()));

        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("fecha"));
    }

    @Test
    @DisplayName("Crear una tarea con fecha futura es permitido")
    void crearConFechaFuturaEsPermitido() {
        Set<ConstraintViolation<TareaCreacionDTO>> violations =
                validator.validate(crearDtoConFecha(LocalDate.now().plusDays(1)));

        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("fecha"));
    }

    @Test
    @DisplayName("Crear una tarea sin fecha es rechazado")
    void crearSinFechaEsRechazado() {
        Set<ConstraintViolation<TareaCreacionDTO>> violations =
                validator.validate(crearDtoConFecha(null));

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("fecha"));
    }
}
