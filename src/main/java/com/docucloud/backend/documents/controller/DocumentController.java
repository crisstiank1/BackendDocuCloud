package com.docucloud.backend.documents.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.request.ShareRequest;
import com.docucloud.backend.documents.dto.response.*;
import com.docucloud.backend.documents.service.DocumentService;
import com.docucloud.backend.documents.service.FolderService;
import com.docucloud.backend.documents.service.ShareService;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    // ── Upload ──────────────────────────────────────────────────────────────

    @PostMapping("/upload/init")
    public ResponseEntity<InitUploadResponse> initUpload(
            @RequestBody InitUploadRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(documentService.initUpload(userId, request));
    }

    @PostMapping("/{documentId}/upload/complete")
    public ResponseEntity<Void> completeUpload(
            @PathVariable Long documentId,
            @RequestBody CompleteUploadRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        documentService.completeUpload(userId, documentId, request);
        return ResponseEntity.ok().build();
    }

    // ── Listado y búsqueda ──────────────────────────────────────────────────

    /**
     * RF-25: usa listWithFavorites para que cada documento traiga isFavorite.
     */
    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(documentService.listWithFavorites(userId, pageable));
    }

    /**
     * RF-25: panel de documentos recientes con flag isFavorite incluido.
     */
    @GetMapping("/recent")
    public ResponseEntity<Page<DocumentResponse>> recentDocuments(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(documentService.getRecentDocumentsWithFavorites(userId, pageable));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<DocumentResponse>> activityHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        // history reutiliza recent internamente; también enriquecemos con isFavorite
        return ResponseEntity.ok(documentService.getRecentDocumentsWithFavorites(userId, pageable));
    }

    /**
     * RF-25: búsqueda avanzada con flag isFavorite en cada resultado.
     */
    @GetMapping("/search")
    public ResponseEntity<Page<DocumentResponse>> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(documentService.searchWithFavorites(
                userId, query, mimeType, status, fromDate, toDate, pageable));
    }

    // ── Descarga ────────────────────────────────────────────────────────────

    @GetMapping("/{documentId}/download")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable Long documentId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(documentService.getDownloadUrl(userId, documentId));
    }

    // ── Compartir ───────────────────────────────────────────────────────────

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
    public ResponseEntity<ShareAccessResponse> accessShare(
            @PathVariable UUID shareId,
            @RequestParam(required = false) String password) {
        return ResponseEntity.ok(shareService.accessShare(shareId, password));
    }

    @GetMapping("/shares/mine")
    public ResponseEntity<Page<ShareSummaryResponse>> getMyShares(
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(shareService.getMyShares(getUserId(auth), includeRevoked, pageable));
    }

    @PostMapping("/shares/{shareId}/write-url")
    public ResponseEntity<PresignedUrlResponse> getWriteUrl(
            @PathVariable UUID shareId,
            @RequestParam(required = false) String password,
            @RequestParam String mimeType) {
        return ResponseEntity.ok(shareService.getWriteUrl(shareId, password, mimeType, null));
    }

    // ── Carpetas ────────────────────────────────────────────────────────────

    @PatchMapping("/{docId}/folder/{folderId}")
    public ResponseEntity<DocumentResponse> moveToFolder(
            @PathVariable Long docId,
            @PathVariable Long folderId,
            Authentication auth) {
        return ResponseEntity.ok(folderService.moveToFolder(getUserId(auth), docId, folderId));
    }

    @DeleteMapping("/{docId}/folder")
    public ResponseEntity<DocumentResponse> removeFromFolder(
            @PathVariable Long docId,
            Authentication auth) {
        return ResponseEntity.ok(folderService.removeFromFolder(getUserId(auth), docId));
    }

    // ── Eliminar ────────────────────────────────────────────────────────────

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        documentService.softDelete(userId, documentId);
        return ResponseEntity.noContent().build();
    }
}
