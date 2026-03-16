package com.docucloud.backend.config.security.jwt;

import com.docucloud.backend.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class InactivityFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Value("${docucloud.app.inactivityTimeoutMs}")
    private long inactivityTimeoutMs;

    public InactivityFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = auth.getName();
        Instant now = Instant.now();

        // ✅ TODO: verificación y update de inactividad movidos a hilo separado
        // para evitar deadlock con la transacción del AuthTokenFilter.
        CompletableFuture.runAsync(() -> {
            try {
                Instant lastActivity = userRepository.findLastActivityByEmail(email);

                boolean isExpired = lastActivity != null &&
                        (now.toEpochMilli() - lastActivity.toEpochMilli()) > inactivityTimeoutMs;

                if (!isExpired) {
                    userRepository.updateLastActivity(email, now);
                }
                // Nota: si está expirado no podemos redirigir desde aquí (hilo async)
                // La expiración por inactividad se maneja en el próximo request síncrono
            } catch (Exception e) {
                System.err.println(">>> InactivityFilter async ERROR: " + e.getMessage());
            }
        });

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/api/health/")
                || path.startsWith("/api/dev/");
    }

    private void sendInactivityError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        new ObjectMapper().writeValue(response.getWriter(), Map.of(
                "error", "SESSION_EXPIRED",
                "message", "Tu sesión expiró por inactividad. Inicia sesión de nuevo."
        ));
    }
}