package com.tareas.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/h2-console")
                || path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = obtenerIp(request);
        boolean permitido;

        if (path.equals("/auth/login")) {
            permitido = rateLimitService.tryConsumeLogin(clientKey);
        } else if (path.equals("/auth/registro")) {
            permitido = rateLimitService.tryConsumeRegistro(clientKey);
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String key = (authentication != null && authentication.isAuthenticated())
                    ? authentication.getName()
                    : clientKey;
            permitido = rateLimitService.tryConsumeApi(key);
        }

        if (!permitido) {
            log.warn("Rate limit superado para {} en {}", clientKey, path);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    JsonResponses.body(429, "Too Many Requests", "Demasiadas solicitudes, inténtalo más tarde"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String obtenerIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        return request.getRemoteAddr();
    }
}
