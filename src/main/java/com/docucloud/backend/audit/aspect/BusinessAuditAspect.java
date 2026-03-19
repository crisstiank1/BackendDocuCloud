package com.docucloud.backend.audit.aspect;

import com.docucloud.backend.audit.annotation.Audited;
import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
public class BusinessAuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public BusinessAuditAspect(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object auditedMethod(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Long userId = resolveUserId();
        boolean success = true;

        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            success = false;
            throw ex;
        } finally {
            String resourceType = audited.resourceType().isBlank()
                    ? pjp.getTarget().getClass().getSimpleName()
                    : audited.resourceType();

            Long resourceId = resolveResourceId(pjp, audited.resourceIdArgIndex());

            ObjectNode details = objectMapper.createObjectNode();
            details.put("method", pjp.getSignature().toShortString());

            String resourceName = extractResourceName(pjp.getArgs());
            if (resourceName != null) {
                details.put("name", resourceName);
            }

            auditService.logBusiness(
                    userId,
                    audited.action(),
                    resourceType,
                    resourceId,
                    success,
                    details
            );
        }
    }

    /**
     * Busca el nombre del recurso en los argumentos del método.
     *
     * REGLA CLAVE: se omiten deliberadamente los tipos de Spring Security
     * (Authentication, UserDetails, UserDetailsImpl) y tipos de infraestructura
     * (Pageable, Long, Integer) porque también tienen getName() o toString()
     * que devuelven el email u otros valores que no son nombres de recursos.
     *
     * Solo se inspeccionan DTOs de request (objetos de dominio propios)
     * que tengan getters de nombre, y Strings cortos directos.
     */
    private String extractResourceName(Object[] args) {
        if (args == null) return null;

        // Tipos que NUNCA deben ser inspeccionados para nombre de recurso
        for (Object arg : args) {
            if (arg == null)                      continue;
            if (arg instanceof Authentication)    continue;  // ← FIX: tenía getName() = email
            if (arg instanceof UserDetails)        continue;  // ← FIX: tenía getUsername() = email
            if (arg instanceof UserDetailsImpl)    continue;  // ← FIX: tipo propio
            if (arg instanceof Pageable)           continue;  // infraestructura
            if (arg instanceof Long)               continue;  // IDs numéricos
            if (arg instanceof Integer)            continue;  // IDs numéricos
            if (arg instanceof Boolean)            continue;  // flags

            // String directo y corto — puede ser un nombre pasado como @PathVariable
            if (arg instanceof String s && !s.isBlank() && s.length() < 200) {
                // Excluir strings que parecen emails o UUIDs
                if (!s.contains("@") && !s.matches("[0-9a-f-]{36}")) {
                    return s;
                }
            }

            // DTO de dominio — busca getters en orden de prioridad
            String[] getterNames = {
                    "getFileName",  // documentos
                    "getName",      // carpetas, categorías
                    "getTitle",     // genérico
                    "getNewName",   // rename requests
                    "getQuery",     // búsquedas
            };

            for (String getter : getterNames) {
                try {
                    Method m = arg.getClass().getMethod(getter);
                    Object value = m.invoke(arg);
                    if (value instanceof String s && !s.isBlank()) {
                        return s;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Este DTO no tiene este getter, continuar
                } catch (Exception ignored) {
                    // Cualquier otro error, continuar
                }
            }
        }
        return null;
    }

    private Long resolveResourceId(ProceedingJoinPoint pjp, int index) {
        if (index < 0) return null;
        Object[] args = pjp.getArgs();
        if (args == null || index >= args.length) return null;
        Object arg = args[index];
        if (arg instanceof Long l)    return l;
        if (arg instanceof Integer i) return i.longValue();
        return null;
    }

    private Long resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetailsImpl u) return u.getId();
        } catch (Exception ignored) {}
        return null;
    }
}