package com.docucloud.backend.audit.service;

import com.docucloud.backend.audit.model.ActivityHistory;
import com.docucloud.backend.audit.repository.ActivityHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;

@Slf4j
@Service
public class AuditService {

    private final ActivityHistoryRepository repository;

    public AuditService(ActivityHistoryRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBusiness(Long userId, String action, String resourceType,
                            Long resourceId, Boolean success, JsonNode details) {
        try {
            // Reusamos logHttp para no duplicar lógica (ip/userAgent pueden ser null).
            logHttp(userId, action, resourceType, resourceId, success, null, null, details);
        } catch (Exception e) {
            log.error("[Audit] Error guardando log negocio", e);
        }
    }

    // Async + nueva transacción: el log se guarda aunque el request falle
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String resourceType,
                    Long resourceId, Boolean success, String ipAddress) {
        try {
            InetAddress inet = toInet(ipAddress);

            ActivityHistory entry = new ActivityHistory(
                    userId, action, resourceType, resourceId, success, inet
            );

            repository.save(entry);
        } catch (Exception e) {
            log.error("[Audit] Error guardando log de auditoría", e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logHttp(Long userId, String action, String resourceType,
                        Long resourceId, Boolean success, String ipAddress,
                        String userAgent, JsonNode details) {
        try {
            InetAddress inet = toInet(ipAddress);

            ActivityHistory entry = new ActivityHistory(
                    userId, action, resourceType, resourceId, success, inet
            );

            entry.setUserAgent(userAgent);
            entry.setDetails(details);

            repository.save(entry);
        } catch (Exception e) {
            log.error("[Audit] Error guardando log HTTP", e);
        }
    }

    private InetAddress toInet(String ipAddress) {
        try {
            if (ipAddress == null || ipAddress.isBlank()) return null;
            return InetAddress.getByName(ipAddress);
        } catch (Exception e) {
            // Si llega algo raro, no rompas el request: guarda NULL y loguea.
            log.warn("[Audit] IP inválida para inet: {}", ipAddress);
            return null;
        }
    }
}
