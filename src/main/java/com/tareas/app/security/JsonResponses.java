package com.tareas.app.security;

import java.time.LocalDateTime;

public final class JsonResponses {

    private JsonResponses() {
    }

    public static String body(int status, String error, String message) {        return "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}"
                .formatted(LocalDateTime.now(), status, error, message);
    }
}
