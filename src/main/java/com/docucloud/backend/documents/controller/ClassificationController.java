package com.docucloud.backend.documents.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.documents.dto.response.ClassificationStatsResponse;
import com.docucloud.backend.documents.service.ClassifierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classification")
@RequiredArgsConstructor
public class ClassificationController {

    private final ClassifierService classifierService;

    @GetMapping("/stats")
    public ResponseEntity<ClassificationStatsResponse> getStats(Authentication auth) {
        Long userId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        return ResponseEntity.ok(classifierService.getStats(userId));
    }
}