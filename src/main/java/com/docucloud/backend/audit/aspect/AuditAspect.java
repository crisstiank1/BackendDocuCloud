package com.docucloud.backend.audit.aspect;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
//@Aspect
//@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("execution(* com.docucloud.backend..controller..*(..))")
    public Object auditControllerCall(ProceedingJoinPoint pjp) throws Throwable {

        String action    = pjp.getSignature().getName(); // o .toUpperCase()
        String className = pjp.getTarget().getClass().getSimpleName();
        Long userId      = resolveUserId();
        String ip        = resolveIp();
        boolean success  = true;

        log.info("[AuditAspect] Interceptando {}.{} userId={} ip={}", className, action, userId, ip);

        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            success = false;
            throw ex;
        } finally {
            auditService.log(userId, action, className, null, success, ip);
        }
    }

    private Long resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;

            Object principal = auth.getPrincipal();
            if (principal instanceof UserDetailsImpl u) {
                return u.getId();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;

            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            String ip = (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : req.getRemoteAddr();

            return (ip == null || ip.isBlank()) ? null : ip;
        } catch (Exception ignored) {
            return null;
        }
    }
}
