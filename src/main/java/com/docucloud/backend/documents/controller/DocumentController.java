package com.docucloud.backend.documents.controller;

import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.request.ShareRequest;
import com.docucloud.backend.documents.dto.response.*;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.service.DocumentService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.documents.service.FolderService;
import com.docucloud.backend.documents.service.ShareService;
import com.docucloud.backend.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;
    private final UserService userService;
    private final FolderService folderService;

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
    }

    private void validateUserExists(Long userId) {
        if (!userService.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
    }

    private Page<DocumentResponse> safeMap(Page<Document> page) {
        return page.map(doc -> DocumentResponse.from(doc));  // Ya null-safe
    }

    @PostMapping("/upload/init")
    public ResponseEntity<InitUploadResponse> initUpload(
            @RequestBody InitUploadRequest request,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();

        InitUploadResponse response = documentService.initUpload(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentId}/upload/complete")
    public ResponseEntity<Void> completeUpload(
            @PathVariable Long documentId,
            @RequestBody CompleteUploadRequest request,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();

        documentService.completeUpload(userId, documentId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();

        Page<Document> documents = documentService.list(userId, pageable);
        return ResponseEntity.ok(safeMap(documents));
    }

    @GetMapping("/recent")
    public ResponseEntity<Page<DocumentResponse>> recentDocuments(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();

        Page<Document> documents = documentService.getRecentDocuments(userId, pageable);
        return ResponseEntity.ok(safeMap(documents));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<DocumentResponse>> activityHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        Page<Document> history = documentService.getActivityHistory(userId, pageable);
        return ResponseEntity.ok(safeMap(history));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable Long documentId,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        DownloadUrlResponse response = documentService.getDownloadUrl(userId, documentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}/delete/confirm")
    public ResponseEntity<DocumentResponse> confirmDelete(
            @PathVariable Long documentId,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        Document document = documentService.getDocumentForDelete(userId, documentId);
        return ResponseEntity.ok(DocumentResponse.from(document));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DocumentResponse>> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) String status,  // ← NUEVO: parámetro status
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();

        Page<Document> result = documentService.search(userId, query, mimeType, status, fromDate, toDate, pageable);
        return ResponseEntity.ok(safeMap(result));
    }


    @PutMapping("/{docId}/share")
    public ResponseEntity<ShareResponse> shareDocument(
            @PathVariable Long docId,
            @Valid @RequestBody ShareRequest request,
            Authentication auth) {
        return ResponseEntity.ok(shareService.shareDocument(docId, request, getUserId(auth)));
    }

    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<Void> revokeShare(
            @PathVariable UUID shareId,
            Authentication auth) {
        shareService.revokeShare(shareId, getUserId(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shares/{shareId}/access")
    public ResponseEntity<ShareAccessResponse> accessShare(  // ← ShareAccessResponse, no PresignedUrlResponse
                                                             @PathVariable UUID shareId,
                                                             @RequestParam(required = false) String password) {
        return ResponseEntity.ok(shareService.accessShare(shareId, password));
    }


    // FOLDERS
    @PatchMapping("/{docId}/folder/{folderId}")
    public ResponseEntity<DocumentResponse> moveToFolder(
            @PathVariable Long docId,
            @PathVariable Long folderId,
            Authentication auth) {
        return ResponseEntity.ok(
                folderService.moveToFolder(getUserId(auth), docId, folderId));
    }

    @DeleteMapping("/{docId}/folder")
    public ResponseEntity<DocumentResponse> removeFromFolder(
            @PathVariable Long docId,
            Authentication auth) {
        return ResponseEntity.ok(
                folderService.removeFromFolder(getUserId(auth), docId));
    }


    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId,
            Authentication authentication) {

        Long userId = ((UserDetailsImpl) authentication.getPrincipal()).getId();
        documentService.softDelete(userId, documentId);
        return ResponseEntity.noContent().build();
    }
}
