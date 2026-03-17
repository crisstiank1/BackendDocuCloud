package com.docucloud.backend.audit.interceptor;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class HttpAuditInterceptor implements HandlerInterceptor {

    private static final String START = "auditStartNanos";

    private static final Set<String> SAFE_QUERY_KEYS = Set.of("page", "size", "sort", "q", "filter");

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public HttpAuditInterceptor(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {

        String method = request.getMethod();
        String uri    = request.getRequestURI();

        // ── FILTRO 1: GETs — solo registrar acciones reales del usuario ────────
        if ("GET".equalsIgnoreCase(method)) {
            boolean isRealUserAction =
                    uri.matches(".*/documents/\\d+/download.*") ||
                            uri.contains("/documents/search");

            if (!isRealUserAction) return;
        }

        // ── FILTRO 2: rutas internas del sistema — nunca auditar ───────────────
        if (uri.startsWith("/api/auth/refresh"))     return;
        if (uri.startsWith("/api/auth/me"))          return;
        if (uri.startsWith("/api/users/me"))         return;
        if (uri.startsWith("/api/admin/audit"))      return;
        if (uri.startsWith("/actuator"))             return;

        // ── A partir de aquí: POST, PUT, PATCH, DELETE + GETs reales ──────────
        long start = (request.getAttribute(START) instanceof Long v) ? v : System.nanoTime();
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        int status = response.getStatus();
        boolean success = status < 400 && ex == null;

        Long userId = resolveUserId();
        String ip = resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        ObjectNode details = objectMapper.createObjectNode();
        details.put("method", method);
        details.put("uri", uri);
        details.put("status", status);
        details.put("durationMs", durationMs);

        boolean hasQuery = request.getQueryString() != null;
        details.put("queryPresent", hasQuery);

        if (hasQuery && "GET".equalsIgnoreCase(method)) {
            ObjectNode q = objectMapper.createObjectNode();
            for (String key : SAFE_QUERY_KEYS) {
                String[] values = request.getParameterValues(key);
                if (values == null || values.length == 0) continue;

                String value = (values.length == 1) ? values[0] : String.join(",", values);
                if (value != null && value.length() > 200) value = value.substring(0, 200);

                q.put(key, value);
            }
            details.set("query", q);
        }

        if (ex != null) {
            details.put("exception", ex.getClass().getSimpleName());
        }

        auditService.logHttp(
                userId,
                "HTTP_REQUEST",
                "HTTP",
                null,
                success,
                ip,
                userAgent,
                details
        );
    }

    private Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetailsImpl u) return u.getId();

        return null;
    }

    private String resolveIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? null : ip;
    }
}
