package com.tareas.app.auth;

import com.tareas.app.model.RefreshToken;
import com.tareas.app.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String PASSWORD = "Prueba123";
    private static final AtomicInteger SUF = new AtomicInteger();

    private String email() {
        return "rt" + SUF.incrementAndGet() + "@test.local";
    }

    private long registrar(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rt-user" + SUF.get() + "\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String sha256Hex(String valor) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Login correcto devuelve access token, refreshToken y mantiene los campos previos")
    void loginDevuelveAccessYRefreshManteniendoCampos() throws Exception {
        String email = email();
        registrar(email);
        JsonNode body = login(email);

        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("type").asText()).isEqualTo("Bearer");
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("username").asText()).isEqualTo("rt-user" + SUF.get());
        assertThat(body.get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("Refresh token valido produce nuevo access token y nuevo refresh token")
    void refreshProduceNuevosTokens() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);

        JsonNode refreshBody = refresh(loginBody.get("refreshToken").asText());

        assertThat(refreshBody.get("token").asText()).isNotBlank();
        assertThat(refreshBody.get("refreshToken").asText()).isNotBlank();
        assertThat(refreshBody.get("type").asText()).isEqualTo("Bearer");
        assertThat(refreshBody.get("refreshToken").asText())
                .isNotEqualTo(loginBody.get("refreshToken").asText());

        // El nuevo access token es valido contra la seguridad existente
        mockMvc.perform(get("/tareas")
                        .header("Authorization", "Bearer " + refreshBody.get("token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rotacion: el refresh token anterior queda invalidado y reutilizarlo falla")
    void rotacionInvalidaElTokenAnterior() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);
        String refreshTokenAnterior = loginBody.get("refreshToken").asText();

        JsonNode refreshBody = refresh(refreshTokenAnterior);
        assertThat(refreshBody.get("refreshToken").asText()).isNotEqualTo(refreshTokenAnterior);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshTokenAnterior + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh con token inexistente/aleatorio falla de forma controlada")
    void refreshConTokenInexistenteFalla() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token-aleatorio-que-no-existe\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token de refresco inválido o expirado"));
    }

    @Test
    @DisplayName("Refresh token expirado falla")
    void refreshTokenExpiradoFalla() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);
        String raw = loginBody.get("refreshToken").asText();

        RefreshToken entidad = refreshTokenRepository.findByTokenHash(sha256Hex(raw)).orElseThrow();
        entidad.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(entidad);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + raw + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh token revocado falla")
    void refreshTokenRevocadoFalla() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);
        String raw = loginBody.get("refreshToken").asText();

        RefreshToken entidad = refreshTokenRepository.findByTokenHash(sha256Hex(raw)).orElseThrow();
        entidad.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(entidad);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + raw + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout invalida el refresh token y ya no puede usarse para refrescar")
    void logoutInvalidaElRefreshToken() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);
        String raw = loginBody.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + raw + "\"}"))
                .andExpect(status().isNoContent());

        // Idempotente: un segundo logout no falla
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + raw + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + raw + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El access token nuevo sigue funcionando con la seguridad existente")
    void accessTokenNuevoFuncionaEnEndpointProtegido() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);

        JsonNode refreshBody = refresh(loginBody.get("refreshToken").asText());

        mockMvc.perform(get("/tareas")
                        .header("Authorization", "Bearer " + refreshBody.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("La base de datos almacena solo el hash del refresh token, nunca el token en claro")
    void persistenciaSoloGuardaHash() throws Exception {
        String email = email();
        registrar(email);
        JsonNode loginBody = login(email);
        String raw = loginBody.get("refreshToken").asText();
        String hashEsperado = sha256Hex(raw);

        RefreshToken entidad = refreshTokenRepository.findByTokenHash(hashEsperado).orElseThrow();

        assertThat(entidad.getTokenHash()).isEqualTo(hashEsperado);
        assertThat(entidad.getTokenHash()).isNotEqualTo(raw);
        assertThat(refreshTokenRepository.findByTokenHash(raw)).isEmpty();

        Integer enClaro = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, raw);
        assertThat(enClaro).isZero();
    }

    @Test
    @DisplayName("Login incorrecto no genera refresh token")
    void loginIncorrectoNoGeneraRefreshToken() throws Exception {
        String email = email();
        registrar(email);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"contrasena-incorrecta\"}"))
                .andExpect(status().isUnauthorized());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens rt JOIN usuarios u ON u.id = rt.usuario_id WHERE u.email = ?",
                Integer.class, email);
        assertThat(count).isZero();
    }
}