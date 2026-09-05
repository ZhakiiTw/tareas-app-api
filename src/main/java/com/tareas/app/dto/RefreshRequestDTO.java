package com.tareas.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequestDTO {

    @NotBlank(message = "El refreshToken es obligatorio")
    private String refreshToken;
}