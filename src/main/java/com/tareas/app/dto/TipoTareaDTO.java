package com.tareas.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TipoTareaDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private String color;
}