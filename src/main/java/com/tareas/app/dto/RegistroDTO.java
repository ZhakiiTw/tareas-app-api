package com.tareas.app.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegistroDTO {

    @NotBlank(message = "El username es obligatorio")
    @Size(min = 3, max = 20, message = "El username debe tener entre 3 y 20 caracteres")
    private String username;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe tener formato válido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).+$",
            message = "La contraseña debe contener al menos una letra y un número")
    private String password;

    // No incluimos ID porque lo genera la BD
}
