package com.tareas.app.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    @Test
    @DisplayName("Login: las primeras 5 peticiones pasan y la 6a se rechaza")
    void loginAdmiteCincoYRechazaLaSexta() {
        RateLimitService service = new RateLimitService();
        String key = "192.0.2.1";

        for (int i = 0; i < 5; i++) {
            assertThat(service.tryConsumeLogin(key)).isTrue();
        }
        assertThat(service.tryConsumeLogin(key)).isFalse();
    }

    @Test
    @DisplayName("Registro: admite 3 por hora y rechaza la 4a")
    void registroAdmiteTresYRechazaLaCuarta() {
        RateLimitService service = new RateLimitService();
        String key = "192.0.2.2";

        for (int i = 0; i < 3; i++) {
            assertThat(service.tryConsumeRegistro(key)).isTrue();
        }
        assertThat(service.tryConsumeRegistro(key)).isFalse();
    }

    @Test
    @DisplayName("Buckets independientes por IP")
    void bucketsIndependientesPorIp() {
        RateLimitService service = new RateLimitService();
        assertThat(service.tryConsumeLogin("ip-a")).isTrue();
        assertThat(service.tryConsumeLogin("ip-b")).isTrue();
        for (int i = 0; i < 4; i++) {
            service.tryConsumeLogin("ip-a");
        }
        assertThat(service.tryConsumeLogin("ip-a")).isFalse();
        assertThat(service.tryConsumeLogin("ip-b")).isTrue();
    }
}
