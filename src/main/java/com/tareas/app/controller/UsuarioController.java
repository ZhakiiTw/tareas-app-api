package com.tareas.app.controller;

import com.tareas.app.dto.UsuarioDTO;
import com.tareas.app.exception.ResourceNotFoundException;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.UsuarioRepository;
import com.tareas.app.service.util.MapeadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final MapeadorService mapeadorService;

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> yo(@AuthenticationPrincipal UserDetails userDetails) {
        String email = userDetails.getUsername();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return ResponseEntity.ok(mapeadorService.toUsuarioDTO(usuario));
    }
}