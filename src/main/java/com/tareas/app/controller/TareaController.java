package com.tareas.app.controller;

import com.tareas.app.dto.TareaActualizacionDTO;
import com.tareas.app.dto.TareaCreacionDTO;
import com.tareas.app.dto.TareaDTO;
import com.tareas.app.service.TareaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareaService;

    @GetMapping
    public ResponseEntity<List<TareaDTO>> listarMisTareas(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();
        log.info("Petición para listar tareas del usuario: {}", email);
        return ResponseEntity.ok(tareaService.listarPorUsuario(email));
    }

    @PostMapping
    public ResponseEntity<TareaDTO> crearTarea(
            @Valid @RequestBody TareaCreacionDTO tareaCreacionDTO,
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        log.info("Petición para crear tarea - usuario: {}", email);

        TareaDTO tareaCreada = tareaService.crearTarea(tareaCreacionDTO, email);
        return new ResponseEntity<>(tareaCreada, HttpStatus.CREATED);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TareaDTO> actualizarTareaParcial(
            @PathVariable Long id,
            @Valid @RequestBody TareaActualizacionDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        log.info("Petición PATCH tarea ID: {} - usuario: {}", id, email);

        TareaDTO tareaActualizada = tareaService.actualizarTareaParcial(id, dto, email);
        return ResponseEntity.ok(tareaActualizada);
    }

    @PatchMapping("/{id}/completar")
    public ResponseEntity<TareaDTO> marcarComoCompletada(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        log.info("Petición completar tarea ID: {} - usuario: {}", id, email);

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setCompletada(true);

        return ResponseEntity.ok(tareaService.actualizarTareaParcial(id, dto, email));
    }

    @PatchMapping("/{id}/reabrir")
    public ResponseEntity<TareaDTO> reabrirTarea(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        log.info("Petición reabrir tarea ID: {} - usuario: {}", id, email);

        TareaActualizacionDTO dto = new TareaActualizacionDTO();
        dto.setCompletada(false);

        return ResponseEntity.ok(tareaService.actualizarTareaParcial(id, dto, email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarTarea(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();
        log.info("Petición DELETE tarea ID: {} - usuario: {}", id, email);

        tareaService.eliminarTarea(id, email);
        return ResponseEntity.noContent().build();
    }
}