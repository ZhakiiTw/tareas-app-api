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
    @Size(max = 254, message = "El email no puede exceder 254 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).+$",
            message = "La contraseña debe contener al menos una letra y un número")
    private String password;

    // No incluimos ID porque lo genera la BD
}
