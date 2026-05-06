package com.tareas.app.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class TareaCreacionDTO {

    @NotBlank(message = "El título es obligatorio")
    @Size(min = 3, max = 100, message = "El título debe tener entre 3 y 100 caracteres")
    private String titulo;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @NotNull(message = "La fecha es obligatoria")
    @FutureOrPresent(message = "La fecha debe ser futura")
    private LocalDate fecha;

    private Boolean completada = false;

    @Min(value = 0, message = "La urgencia mínima es 0")
    @Max(value = 2, message = "La urgencia máxima es 2")
    private Integer urgencia = 0;

    @NotNull(message = "El tipo de tarea es obligatorio")
    private Long tipoTareaId;
}