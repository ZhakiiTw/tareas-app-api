package com.tareas.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    private Long id;
    private String username;
    private String email;
    // ¡NO incluimos la contraseña aquí!
    // Sin validaciones aquí porque es solo para mostrar
}
