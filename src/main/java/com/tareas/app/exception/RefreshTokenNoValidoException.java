package com.tareas.app.exception;

public class RefreshTokenNoValidoException extends RuntimeException {

    public RefreshTokenNoValidoException() {
        super("Refresh token inválido");
    }
}