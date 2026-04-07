package com.tareas.app.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class TareaActualizacionDTO {

    @Size(min = 3, max = 100, message = "El título debe tener entre 3 y 100 caracteres")
    private String titulo;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @FutureOrPresent(message = "La fecha debe ser futura o presente")
    private LocalDate fecha;

    private Boolean completada;
}