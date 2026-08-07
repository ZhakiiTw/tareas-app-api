package com.tareas.app.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.issuer}")
    private String jwtIssuer;

    @Value("${jwt.audience}")
    private String jwtAudience;

    private static final String PASSWORD = "Prueba123";
    private static final AtomicInteger SUF = new AtomicInteger();

    private SecretKey testKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    // Contador global (sin reset) para emails unicos entre todos los tests de la clase
    private String email() {
        return "user" + SUF.incrementAndGet() + "@test.local";
    }

    private long registrar(String email, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + SUF.get() + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long crearTipo(String token, String nombre) throws Exception {
        MvcResult result = mockMvc.perform(post("/tipos-tarea")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"color\":\"#123456\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long crearTarea(String token, long tipoId, String titulo) throws Exception {
        MvcResult result = mockMvc.perform(post("/tareas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + titulo + "\",\"fecha\":\"" + java.time.LocalDate.now().plusDays(1) + "\",\"tipoTareaId\":" + tipoId + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("Endpoint privado sin token devuelve 401")
    void endpointPrivadoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/tareas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token malformado devuelve 401")
    void tokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/tareas").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT con firma manipulada es rechazado con 401")
    void firmaManipuladaEsRechazada() throws Exception {
        String email = email();
        registrar(email, "firma");
        String token = login(email);
        String tampered = token.substring(0, token.length() - 2) + "ab";

        mockMvc.perform(get("/tareas").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT expirado devuelve 401")
    void tokenExpiradoDevuelve401() throws Exception {
        String email = email();
        registrar(email, "expirado");
        String expired = Jwts.builder()
                .subject(email)
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .issuedAt(new Date(System.currentTimeMillis() - 200_000))
                .expiration(new Date(System.currentTimeMillis() - 100_000))
                .signWith(testKey())
                .compact();

        mockMvc.perform(get("/tareas").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT con claims manipulados (subject de otro usuario) es rechazado")
    void claimsManipuladosSonRechazados() throws Exception {
        String email = email();
        registrar(email, "legit");
        registrar("otro@test.local", "otro");

        String forged = Jwts.builder()
                .subject("otro@test.local")
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(testKey())
                .compact();

        // El token es valido, pero pertenece a otro usuario; el acceso a /tareas
        // debe devolver SOLO los recursos del usuario autenticado, nunca los ajenos.
        mockMvc.perform(get("/tareas").header("Authorization", "Bearer " + forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Usuario B no puede leer/editar/eliminar/completar/reabrir tarea de A (404)")
    void usuarioBNoPuedeTocarTareaDeA() throws Exception {
        String emailA = email();
        registrar(emailA, "usuarioa");
        String tokenA = login(emailA);
        long tipoA = crearTipo(tokenA, "tipo-a");
        long tareaA = crearTarea(tokenA, tipoA, "tarea-de-a");

        String emailB = email();
        registrar(emailB, "usuariob");
        String tokenB = login(emailB);

        // No existe GET /tareas/{id}; la lectura se comprueba vía listado (no revela tareas ajenas)
        MvcResult listaB = mockMvc.perform(get("/tareas").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tareasDeB = objectMapper.readTree(listaB.getResponse().getContentAsString());
        assertThat(tareasDeB.size()).isZero();

        mockMvc.perform(patch("/tareas/{id}", tareaA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"hack\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/tareas/{id}/completar", tareaA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/tareas/{id}/reabrir", tareaA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/tareas/{id}", tareaA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // El propietario puede completar y reabrir
        mockMvc.perform(patch("/tareas/{id}/completar", tareaA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/tareas/{id}/reabrir", tareaA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Usuario B no puede usar un tipo de tarea de A (404)")
    void usuarioBNoPuedeUsarTipoDeA() throws Exception {
        String emailA = email();
        registrar(emailA, "usuarioa");
        String tokenA = login(emailA);
        long tipoA = crearTipo(tokenA, "tipo-a-uso");

        String emailB = email();
        registrar(emailB, "usuariob");
        String tokenB = login(emailB);

        mockMvc.perform(post("/tareas")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"tarea con tipo ajeno\",\"fecha\":\"" + java.time.LocalDate.now().plusDays(1) + "\",\"tipoTareaId\":" + tipoA + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/tipos-tarea/{id}", tipoA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"hack\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/tipos-tarea/{id}", tipoA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Listado solo devuelve recursos del usuario autenticado")
    void listadoSoloDevuelveRecursosPropios() throws Exception {
        String emailA = email();
        registrar(emailA, "usuarioa");
        String tokenA = login(emailA);
        long tipoA = crearTipo(tokenA, "tipo-lista");
        crearTarea(tokenA, tipoA, "tarea-propia-1");
        crearTarea(tokenA, tipoA, "tarea-propia-2");

        String emailB = email();
        registrar(emailB, "usuariob");
        String tokenB = login(emailB);
        long tipoB = crearTipo(tokenB, "tipo-b");
        crearTarea(tokenB, tipoB, "tarea-de-b");

        MvcResult resultA = mockMvc.perform(get("/tareas").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tareasA = objectMapper.readTree(resultA.getResponse().getContentAsString());
        assertThat(tareasA.size()).isGreaterThanOrEqualTo(2);
        tareasA.forEach(t ->
                assertThat(t.get("usuarioId").asLong()).isEqualTo(tareasA.get(0).get("usuarioId").asLong()));

        MvcResult resultB = mockMvc.perform(get("/tareas").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tareasB = objectMapper.readTree(resultB.getResponse().getContentAsString());
        assertThat(tareasB.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("DTO con titulo demasiado largo devuelve 400")
    void tituloDemasiadoLargoDevuelve400() throws Exception {
        String email = email();
        registrar(email, "usuarioa");
        String token = login(email);
        long tipo = crearTipo(token, "tipo-long");

        String tituloLargo = "t".repeat(101);
        mockMvc.perform(post("/tareas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + tituloLargo + "\",\"fecha\":\"" + java.time.LocalDate.now().plusDays(1) + "\",\"tipoTareaId\":" + tipo + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.titulo").exists());
    }

    @Test
    @DisplayName("Registro con email invalido devuelve 400")
    void emailInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"inv\",\"email\":\"no-es-email\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("Registro duplicado devuelve 409")
    void registroDuplicadoDevuelve409() throws Exception {
        String email = email();
        registrar(email, "dup");
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dup2\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("JSON malformado devuelve 400 sin stack trace")
    void jsonMalformadoDevuelve400SinStackTrace() throws Exception {
        String email = email();
        registrar(email, "usuarioa");
        String token = login(email);

        MvcResult result = mockMvc.perform(post("/tareas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{titulo rotto"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("at com.tareas");
    }

    @Test
    @DisplayName("Endpoint publico funciona sin token (registro)")
    void endpointPublicoFuncionaSinToken() throws Exception {
        String email = email();
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pub\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Metodo HTTP incorrecto devuelve 405")
    void metodoIncorrectoDevuelve405() throws Exception {
        mockMvc.perform(delete("/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Endpoint inexistente autenticado devuelve 404")
    void endpointInexistenteDevuelve404() throws Exception {
        String email = email();
        registrar(email, "inexistente");
        String token = login(email);
        mockMvc.perform(get("/no-existe").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
