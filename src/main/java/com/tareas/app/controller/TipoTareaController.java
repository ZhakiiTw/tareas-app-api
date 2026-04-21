package com.tareas.app.controller;

import com.tareas.app.dto.TipoTareaActualizacionDTO;
import com.tareas.app.dto.TipoTareaCreacionDTO;
import com.tareas.app.dto.TipoTareaDTO;
import com.tareas.app.service.TipoTareaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tipos-tarea")
@RequiredArgsConstructor
public class TipoTareaController {

    private final TipoTareaService tipoTareaService;

    @GetMapping
    public ResponseEntity<List<TipoTareaDTO>> listar(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tipoTareaService.listarPorUsuario(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<TipoTareaDTO> crear(
            @Valid @RequestBody TipoTareaCreacionDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        return new ResponseEntity<>(
                tipoTareaService.crearTipo(dto, userDetails.getUsername()),
                HttpStatus.CREATED
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TipoTareaDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody TipoTareaActualizacionDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tipoTareaService.actualizarTipo(id, dto, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        tipoTareaService.eliminarTipo(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}