package com.tareas.app.dto;

import lombok.Getter;

@Getter
public class LoginResponseDTO {

    private final String token;
    private final String type;
    private final Long id;
    private final String username;
    private final String email;

    public LoginResponseDTO(String token, Long id, String username, String email) {
        this.token = token;
        this.type = "Bearer";
        this.id = id;
        this.username = username;
        this.email = email;
    }
}