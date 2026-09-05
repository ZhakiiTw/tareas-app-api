package com.tareas.app.controller;

import com.tareas.app.dto.LoginDTO;
import com.tareas.app.dto.LoginResponseDTO;
import com.tareas.app.dto.RefreshRequestDTO;
import com.tareas.app.dto.RefreshResponseDTO;
import com.tareas.app.dto.RegistroDTO;
import com.tareas.app.dto.UsuarioDTO;
import com.tareas.app.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    public ResponseEntity<UsuarioDTO> registrarUsuario(@Valid @RequestBody RegistroDTO registroDTO) {
        UsuarioDTO usuarioCreado = authService.registrarUsuario(registroDTO);
        return new ResponseEntity<>(usuarioCreado, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginResponseDTO response = authService.login(loginDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO refreshRequestDTO) {
        RefreshResponseDTO response = authService.refrescar(refreshRequestDTO.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequestDTO refreshRequestDTO) {
        authService.cerrarSesion(refreshRequestDTO.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}