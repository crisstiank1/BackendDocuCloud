package com.docucloud.backend.audit.service;

import com.docucloud.backend.audit.model.ActivityHistory;
import com.docucloud.backend.audit.repository.ActivityHistoryRepository;
import com.docucloud.backend.audit.specification.ActivityHistorySpecification;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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
            logHttp(userId, action, resourceType, resourceId, success, null, null, details);
        } catch (Exception e) {
            log.error("[Audit] Error guardando log negocio", e);
        }
    }

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

    @Transactional(readOnly = true)
    public Page<ActivityHistory> getLogsForAdmin(
            Long userId,
            String action,
            String resourceType,
            String fromDate,
            String toDate,
            Pageable pageable) {

        Instant from = null;
        if (fromDate != null && !fromDate.isBlank())
            from = LocalDate.parse(fromDate).atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant to = null;
        if (toDate != null && !toDate.isBlank())
            to = LocalDate.parse(toDate).atTime(23, 59, 59).atOffset(ZoneOffset.UTC).toInstant();

        return repository.findAll(
                ActivityHistorySpecification.filter(
                        userId,
                        (action != null && !action.isBlank()) ? action.toUpperCase() : null,
                        (resourceType != null && !resourceType.isBlank()) ? resourceType.toUpperCase() : null,
                        from,
                        to
                ),
                pageable
        );
    }

    @Transactional(readOnly = true)
    public long countFailed() {
        return repository.countByIsSuccessfulFalse();
    }

    @Transactional(readOnly = true)
    public long countUniqueUsers() {
        return repository.countDistinctUserId();
    }

    private InetAddress toInet(String ipAddress) {
        try {
            if (ipAddress == null || ipAddress.isBlank()) return null;
            return InetAddress.getByName(ipAddress);
        } catch (Exception e) {
            log.warn("[Audit] IP inválida para inet: {}", ipAddress);
            return null;
        }
    }
}