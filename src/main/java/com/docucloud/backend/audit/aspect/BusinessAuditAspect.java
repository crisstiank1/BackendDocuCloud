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
        Object result = null;

        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable ex) {
            success = false;
            throw ex;
        } finally {
            String resourceType = audited.resourceType().isBlank()
                    ? pjp.getTarget().getClass().getSimpleName()
                    : audited.resourceType();

            Long resourceId = resolveResourceId(pjp, audited.resourceIdArgIndex());

            if (resourceId == null && result != null) {
                resourceId = extractIdFromResult(result);
            }

            ObjectNode details = objectMapper.createObjectNode();
            details.put("method", pjp.getSignature().toShortString());

            String resourceName = extractResourceName(pjp.getArgs());
            if (resourceName != null) {
                details.put("name", resourceName);
            }

            auditService.logBusiness(userId, audited.action(), resourceType, resourceId, success, details);
        }
    }

    private String extractResourceName(Object[] args) {
        if (args == null) return null;

        for (Object arg : args) {
            if (arg == null)                    continue;
            if (arg instanceof Authentication)  continue;
            if (arg instanceof UserDetails)     continue;
            if (arg instanceof UserDetailsImpl) continue;
            if (arg instanceof Pageable)        continue;
            if (arg instanceof Long)            continue;
            if (arg instanceof Integer)         continue;
            if (arg instanceof Boolean)         continue;

            if (arg instanceof String s && !s.isBlank() && s.length() < 200) {
                if (!s.contains("@") && !s.matches("[0-9a-f-]{36}")) {
                    return s;
                }
            }

            // Primero getters de clases normales, luego accessors de Java records
            String[] getterNames = {
                    "getFileName",   // clase normal
                    "getName",       // clase normal
                    "getTitle",      // clase normal
                    "getNewName",    // clase normal
                    "getQuery",      // clase normal
                    "fileName",      // ✅ record accessor
                    "name",          // ✅ record accessor — CreateCategoryRequest, CreateTagRequest, etc.
                    "title",         // ✅ record accessor
                    "newName",       // ✅ record accessor
                    "query",         // ✅ record accessor
            };

            for (String getter : getterNames) {
                try {
                    Method m = arg.getClass().getMethod(getter);
                    Object value = m.invoke(arg);
                    if (value instanceof String s && !s.isBlank()) {
                        return s;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private Long extractIdFromResult(Object result) {
        try {
            Method getId = result.getClass().getMethod("getId");
            Object value = getId.invoke(result);
            if (value instanceof Long l)    return l;
            if (value instanceof Integer i) return i.longValue();
        } catch (Exception ignored) {}
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