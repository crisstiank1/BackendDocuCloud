package com.docucloud.backend.audit.controller;

import com.docucloud.backend.audit.model.ActivityHistory;
import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class MyAuditController {

    private final AuditService auditService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/logs/my")
    public ResponseEntity<Page<ActivityHistory>> getMyLogs(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(
                auditService.getLogsForUser(userDetails.getId(), pageable)
        );
    }
}