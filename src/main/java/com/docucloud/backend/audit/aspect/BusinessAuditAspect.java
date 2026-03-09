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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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

            // Captura resourceId por índice si está configurado
            Long resourceId = resolveResourceId(pjp, audited.resourceIdArgIndex());

            ObjectNode details = objectMapper.createObjectNode();
            details.put("method", pjp.getSignature().toShortString());

            auditService.logBusiness(
                    userId,
                    audited.action(),
                    resourceType,
                    resourceId,  // ya no es null cuando se configura
                    success,
                    details
            );
        }
    }

    private Long resolveResourceId(ProceedingJoinPoint pjp, int index) {
        if (index < 0) return null; // no configurado

        Object[] args = pjp.getArgs();
        if (args == null || index >= args.length) return null;

        Object arg = args[index];
        if (arg instanceof Long l) return l;
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
