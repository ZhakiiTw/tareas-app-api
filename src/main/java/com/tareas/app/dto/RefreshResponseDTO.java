package com.tareas.app.dto;

import lombok.Getter;

@Getter
public class RefreshResponseDTO {

    private final String token;
    private final String refreshToken;
    private final String type;

    public RefreshResponseDTO(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.type = "Bearer";
    }
}