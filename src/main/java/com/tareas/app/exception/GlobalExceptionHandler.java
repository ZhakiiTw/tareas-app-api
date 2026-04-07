package com.tareas.app.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> base(HttpStatus status) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", LocalDateTime.now());
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        return m;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.error("❌ Not found: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.NOT_FOUND);
        r.put("message", ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ResourceConflictException ex) {
        log.error("❌ Conflict: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.CONFLICT);
        r.put("message", ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        log.error("❌ Error de validación");
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = ((FieldError) err).getField();
            errors.put(field, err.getDefaultMessage());
        });

        Map<String, Object> r = base(HttpStatus.BAD_REQUEST);
        r.put("errors", errors);
        return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("❌ Bad credentials: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.UNAUTHORIZED);
        r.put("message", "Credenciales inválidas");
        return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("❌ Forbidden: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.FORBIDDEN);
        r.put("message", "No tienes permisos para acceder a este recurso");
        return new ResponseEntity<>(r, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("❌ Error inesperado: {}", ex.getMessage(), ex);
        Map<String, Object> r = base(HttpStatus.INTERNAL_SERVER_ERROR);
        r.put("message", "Ha ocurrido un error interno");
        return new ResponseEntity<>(r, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}