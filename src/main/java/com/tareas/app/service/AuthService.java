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
        String email = normalizarEmail(registroDTO.getEmail());
        log.info("=== REGISTRO === Email: {} / Username: {}", email, registroDTO.getUsername());

        usuarioRepository.findByEmail(email)
                .ifPresent(u -> { throw new ResourceConflictException("El email ya está registrado"); });

        usuarioRepository.findByUsername(registroDTO.getUsername().trim())
                .ifPresent(u -> { throw new ResourceConflictException("El username ya está en uso"); });

        String passwordEncriptada = passwordEncoder.encode(registroDTO.getPassword());

        Usuario nuevoUsuario = Usuario.builder()
                .username(registroDTO.getUsername().trim())
                .email(email)
                .password(passwordEncriptada)
                .build();

        Usuario usuarioGuardado = usuarioRepository.save(nuevoUsuario);
        log.info("Usuario registrado ID: {}", usuarioGuardado.getId());

        return mapeadorService.toUsuarioDTO(usuarioGuardado);
    }

    public LoginResponseDTO login(LoginDTO loginDTO) {
        String email = normalizarEmail(loginDTO.getEmail());
        log.info("=== LOGIN === Email: {}", email);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, loginDTO.getPassword())
        );

        // si falla, lanza BadCredentialsException (lo maneja tu GlobalExceptionHandler)

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String token = jwtService.generateToken(usuario.getEmail());

        return new LoginResponseDTO(
                token,
                usuario.getId(),
                usuario.getUsername(),
                usuario.getEmail()
        );
    }

    private String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}