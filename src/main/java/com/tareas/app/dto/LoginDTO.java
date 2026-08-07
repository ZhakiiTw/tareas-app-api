package com.tareas.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Email inválido")
    @Size(max = 254, message = "El email no puede exceder 254 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 72, message = "La contraseña no puede exceder 72 caracteres")
    private String password;
}