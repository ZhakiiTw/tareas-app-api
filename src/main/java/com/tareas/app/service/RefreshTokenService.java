package com.tareas.app.service;

import com.tareas.app.dto.RefreshResponseDTO;
import com.tareas.app.exception.RefreshTokenNoValidoException;
import com.tareas.app.model.RefreshToken;
import com.tareas.app.model.Usuario;
import com.tareas.app.repository.RefreshTokenRepository;
import com.tareas.app.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    public String crearPara(Usuario usuario) {
        String tokenEnClaro = generarToken();
        LocalDateTime ahora = LocalDateTime.now();

        RefreshToken entidad = RefreshToken.builder()
                .usuario(usuario)
                .tokenHash(sha256Hex(tokenEnClaro))
                .createdAt(ahora)
                .expiresAt(ahora.plusDays(refreshExpirationDays))
                .build();

        refreshTokenRepository.save(entidad);

        return tokenEnClaro;
    }

    @Transactional
    public RefreshResponseDTO rotar(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            throw new RefreshTokenNoValidoException();
        }

        String hash = sha256Hex(tokenEnClaro);
        RefreshToken entidad = refreshTokenRepository.findByTokenHashParaActualizar(hash)
                .orElseThrow(RefreshTokenNoValidoException::new);

        LocalDateTime ahora = LocalDateTime.now();

        if (entidad.getRevokedAt() != null) {
            log.warn("Reintento de refresh token revocado (usuario id={})", entidad.getUsuario().getId());
            throw new RefreshTokenNoValidoException();
        }

        if (entidad.getExpiresAt().isBefore(ahora)) {
            log.warn("Reintento de refresh token expirado (usuario id={})", entidad.getUsuario().getId());
            throw new RefreshTokenNoValidoException();
        }

        entidad.setRevokedAt(ahora);
        refreshTokenRepository.save(entidad);

        String nuevoRefresh = crearPara(entidad.getUsuario());
        String nuevoAccess = jwtService.generateToken(entidad.getUsuario().getEmail());

        limpiarExpirados();

        return new RefreshResponseDTO(nuevoAccess, nuevoRefresh);
    }

    @Transactional
    public void revocar(String tokenEnClaro) {
        if (tokenEnClaro == null || tokenEnClaro.isBlank()) {
            return;
        }

        String hash = sha256Hex(tokenEnClaro);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(entidad -> {
            if (entidad.getRevokedAt() == null) {
                entidad.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(entidad);
                log.debug("Refresh token revocado por logout");
            }
        });
    }

    @Transactional
    public void limpiarExpirados() {
        LocalDateTime ahora = LocalDateTime.now();
        int borrados = refreshTokenRepository.eliminarExpiradosORevocados(ahora, ahora.minusDays(1));
        if (borrados > 0) {
            log.info("Limpiados {} refresh tokens expirados/revocados", borrados);
        }
    }

    private String generarToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}