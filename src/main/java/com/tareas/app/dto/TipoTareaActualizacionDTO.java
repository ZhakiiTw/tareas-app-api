package com.tareas.app.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TipoTareaActualizacionDTO {

    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @Pattern(
            regexp = "^#([A-Fa-f0-9]{6})$",
            message = "El color debe tener formato hexadecimal, por ejemplo #4F46E5"
    )
    private String color;
}