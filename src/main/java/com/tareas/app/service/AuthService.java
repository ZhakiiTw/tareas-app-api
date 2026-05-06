package com.tareas.app.service;

import com.tareas.app.dto.LoginDTO;
import com.tareas.app.dto.LoginResponseDTO;
import com.tareas.app.dto.RegistroDTO;
import com.tareas.app.dto.UsuarioDTO;
import com.tareas.app.exception.ResourceConflictException;
import com.tareas.app.exception.ResourceNotFoundException;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.UsuarioRepository;
import com.tareas.app.security.JwtService;
import com.tareas.app.service.util.MapeadorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MapeadorService mapeadorService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public UsuarioDTO registrarUsuario(RegistroDTO registroDTO) {
        log.info("=== REGISTRO === Email: {} / Username: {}", registroDTO.getEmail(), registroDTO.getUsername());

        usuarioRepository.findByEmail(registroDTO.getEmail())
                .ifPresent(u -> { throw new ResourceConflictException("El email ya está registrado"); });

        usuarioRepository.findByUsername(registroDTO.getUsername())
                .ifPresent(u -> { throw new ResourceConflictException("El username ya está en uso"); });

        String passwordEncriptada = passwordEncoder.encode(registroDTO.getPassword());

        Usuario nuevoUsuario = Usuario.builder()
                .username(registroDTO.getUsername())
                .email(registroDTO.getEmail())
                .password(passwordEncriptada)
                .build();

        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);
        log.info("Usuario registrado ID: {}", usuarioGuardado.getId());

        return mapeadorService.toUsuarioDTO(usuarioGuardado);
    }

    public LoginResponseDTO login(LoginDTO loginDTO) {
        log.info("=== LOGIN === Email: {}", loginDTO.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getEmail(), loginDTO.getPassword())
        );

        // si falla, lanza BadCredentialsException (lo maneja tu GlobalExceptionHandler)

        Usuario usuario = usuarioRepository.findByEmail(loginDTO.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String token = jwtService.generateToken(usuario.getEmail());

        return new LoginResponseDTO(
                token,
                usuario.getId(),
                usuario.getUsername(),
                usuario.getEmail()
        );
    }
}