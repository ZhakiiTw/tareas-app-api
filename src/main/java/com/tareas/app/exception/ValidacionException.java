package com.tareas.app.exception;

public class ValidacionException extends RuntimeException {

    private final String field;

    public ValidacionException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
