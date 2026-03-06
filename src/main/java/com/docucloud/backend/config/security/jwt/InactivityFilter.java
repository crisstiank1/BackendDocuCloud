package com.docucloud.backend.config.security.jwt;

import com.docucloud.backend.users.model.User;
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

        // Solo aplica a usuarios autenticados
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant now = Instant.now();
        Instant lastActivity = user.getLastActivityAt();

        // Si existe lastActivityAt y superó el timeout → rechazar
        if (lastActivity != null) {
            long elapsed = now.toEpochMilli() - lastActivity.toEpochMilli();
            if (elapsed > inactivityTimeoutMs) {
                SecurityContextHolder.clearContext();
                sendInactivityError(response);
                return;
            }
        }

        // Actualizar lastActivityAt en cada request válido
        user.setLastActivityAt(now);
        userRepository.save(user);

        filterChain.doFilter(request, response);
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
