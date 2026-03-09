package com.docucloud.backend.audit.controller;

import com.docucloud.backend.audit.model.ActivityHistory;
import com.docucloud.backend.audit.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * GET /api/admin/audit/logs
     * Solo accesible por ADMIN.
     * Params opcionales: userId, action, resourceType, from (YYYY-MM-DD), to (YYYY-MM-DD)
     * Paginado: ?page=0&size=20&sort=createdAt,desc
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/logs")
    public ResponseEntity<Page<ActivityHistory>> getLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(
                auditService.getLogsForAdmin(userId, action, resourceType, from, to, pageable)
        );
    }
}