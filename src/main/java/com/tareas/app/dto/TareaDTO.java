package com.tareas.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TareaDTO {
    private Long id;
    private String titulo;
    private String descripcion;
    private LocalDate fecha;
    private Boolean completada;
    private Long usuarioId; // Enviamos solo el ID del usuario, no el objeto entero
    private Long tipoTareaId;
    private String tipoTareaNombre; // Aqui las tres funcionalidades del Tipo de Tarea
    private String tipoTareaColor;
}
