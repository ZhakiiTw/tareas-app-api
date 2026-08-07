package com.tareas.app.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        log.warn("Not found: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.NOT_FOUND);
        r.put("message", ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ResourceConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.CONFLICT);
        r.put("message", ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<Map<String, Object>> handleValidacion(ValidacionException ex) {
        log.warn("Error de validación: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getField(), ex.getMessage());

        Map<String, Object> r = base(HttpStatus.BAD_REQUEST);
        r.put("errors", errors);
        return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Error de validación de campos");
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(err -> {
            if (err instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                errors.put(err.getObjectName(), err.getDefaultMessage());
            }
        });

        Map<String, Object> r = base(HttpStatus.BAD_REQUEST);
        r.put("errors", errors);
        return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            jakarta.validation.ConstraintViolationException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        log.warn("Solicitud mal formada: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.BAD_REQUEST);
        r.put("message", "Datos no válidos");
        return new ResponseEntity<>(r, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Método HTTP no soportado: {}", ex.getMessage());
        Map<String, Object> r = base(HttpStatus.METHOD_NOT_ALLOWED);
        r.put("message", "Método no permitido");
        return new ResponseEntity<>(r, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getResourcePath());
        Map<String, Object> r = base(HttpStatus.NOT_FOUND);
        r.put("message", "Recurso no encontrado");
        return new ResponseEntity<>(r, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials");
        Map<String, Object> r = base(HttpStatus.UNAUTHORIZED);
        r.put("message", "Credenciales inválidas");
        return new ResponseEntity<>(r, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Forbidden");
        Map<String, Object> r = base(HttpStatus.FORBIDDEN);
        r.put("message", "No tienes permisos para acceder a este recurso");
        return new ResponseEntity<>(r, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Error inesperado", ex);
        Map<String, Object> r = base(HttpStatus.INTERNAL_SERVER_ERROR);
        r.put("message", "Ha ocurrido un error interno");
        return new ResponseEntity<>(r, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
