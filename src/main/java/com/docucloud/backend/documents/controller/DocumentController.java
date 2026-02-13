package com.docucloud.backend.documents.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.common.dto.MessageResponse;
import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.response.DownloadUrlResponse;
import com.docucloud.backend.documents.dto.response.InitUploadResponse;
import com.docucloud.backend.documents.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping("/init-upload")
    public ResponseEntity<InitUploadResponse> initUpload(@Valid @RequestBody InitUploadRequest req,
                                                         Authentication authentication) {
        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(service.initUpload(userId, req));
    }

    @PostMapping("/{id}/complete-upload")
    public ResponseEntity<MessageResponse> completeUpload(@PathVariable Long id,
                                                          @Valid @RequestBody CompleteUploadRequest req,
                                                          Authentication authentication) {
        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        service.completeUpload(userId, id, req);
        return ResponseEntity.ok(new MessageResponse("Upload completed"));
    }

    @GetMapping
    public ResponseEntity<Page<?>> list(Authentication authentication, Pageable pageable) {
        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(service.list(userId, pageable));
    }

    @GetMapping("/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(@PathVariable Long id, Authentication authentication) {
        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        return ResponseEntity.ok(service.getDownloadUrl(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        service.softDelete(userId, id);
        return ResponseEntity.ok(new MessageResponse("Document moved to trash"));
    }
}
