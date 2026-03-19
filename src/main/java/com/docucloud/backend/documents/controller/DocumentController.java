package com.docucloud.backend.documents.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.request.ShareRequest;
import com.docucloud.backend.documents.dto.request.UpdateSharePermissionRequest;
import com.docucloud.backend.documents.dto.response.*;
import com.docucloud.backend.documents.service.CategoryService;
import com.docucloud.backend.documents.service.DocumentService;
import com.docucloud.backend.documents.service.FolderService;
import com.docucloud.backend.documents.service.ShareService;
import com.docucloud.backend.search.service.SearchHistoryService;
import com.docucloud.backend.tags.dto.response.TagResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ShareService shareService;
    private final SearchHistoryService searchHistoryService;
    private final FolderService folderService;
    private final CategoryService categoryService;

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
    }

    // ── UPLOAD ──────────────────────────────────────────────────────────────

    @PostMapping("/upload/init")
    public ResponseEntity<InitUploadResponse> initUpload(
            @RequestBody @Valid InitUploadRequest request,
            Authentication auth) {
        return ResponseEntity.ok(documentService.initUpload(getUserId(auth), request));
    }

    @PostMapping("/{documentId}/upload/complete")
    public ResponseEntity<Void> completeUpload(
            @PathVariable Long documentId,
            @RequestBody @Valid CompleteUploadRequest request,
            Authentication auth) {
        documentService.completeUpload(getUserId(auth), documentId, request);
        return ResponseEntity.ok().build();
    }

    // ── LISTADO Y BÚSQUEDA ───────────────────────────────────────────────────

    /**
     * GET /api/documents
     * Sin parámetros      → todos los documentos del usuario (paginado)
     * ?categoryId={id}    → solo los documentos de esa categoría (paginado)
     *
     * Ambos casos incluyen el campo isFavorite en la respuesta.
     */
    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {

        Long userId = getUserId(auth);

        if (categoryId != null) {
            return ResponseEntity.ok(
                    documentService.listWithFavoritesByCategory(userId, categoryId, pageable));
        }

        return ResponseEntity.ok(documentService.listWithFavorites(userId, pageable));
    }

    @GetMapping("/recent")
    public ResponseEntity<Page<DocumentResponse>> recentDocuments(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(documentService.getRecentDocumentsWithFavorites(getUserId(auth), pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DocumentResponse>> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {

        Long userId = getUserId(auth);
        if (query != null && !query.trim().isEmpty()) {
            searchHistoryService.saveSearch(userId, query);
        }

        return ResponseEntity.ok(documentService.searchWithFavorites(
                userId, query, mimeType, status, fromDate, toDate, pageable));
    }

    // ── COMPARTIR (SHARE) ───────────────────────────────────────────────────

    @PutMapping("/{docId}/share")
    public ResponseEntity<ShareResponse> shareDocument(
            @PathVariable Long docId,
            @Valid @RequestBody ShareRequest request,
            Authentication auth) {
        return ResponseEntity.ok(shareService.shareDocument(docId, request, getUserId(auth)));
    }

    @GetMapping("/shares/mine")
    public ResponseEntity<Page<ShareSummaryResponse>> getMyShares(
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(shareService.getMyShares(getUserId(auth), includeRevoked, pageable));
    }

    @GetMapping("/shares/received")
    public ResponseEntity<Page<SharedWithMeResponse>> getSharedWithMe(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();
        return ResponseEntity.ok(shareService.getSharedWithMe(user.getEmail(), pageable));
    }

    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<Void> revokeShare(
            @PathVariable UUID shareId,
            Authentication auth) {
        shareService.revokeShare(shareId, getUserId(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/shares/{shareId}/permission")
    public ResponseEntity<ShareResponse> updateSharePermission(
            @PathVariable UUID shareId,
            @Valid @RequestBody UpdateSharePermissionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                shareService.updateSharePermission(shareId, request.getPermission(), getUserId(auth)));
    }

    @GetMapping("/shares/{shareId}/access")
    public ResponseEntity<ShareAccessResponse> accessShare(
            @PathVariable UUID shareId,
            @RequestParam(required = false) String password) {
        return ResponseEntity.ok(shareService.accessShare(shareId, password));
    }

    // ── TAGS ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/tags")
    public List<TagResponse> getDocumentTags(@PathVariable Long id, Authentication auth) {
        return documentService.getDocumentTags(id, getUserId(auth));
    }

    @PutMapping("/{id}/tags/{tagId}")
    public ResponseEntity<Void> addTag(@PathVariable Long id, @PathVariable Long tagId, Authentication auth) {
        documentService.addTagToDocument(id, tagId, getUserId(auth));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ResponseEntity<Void> removeTag(@PathVariable Long id, @PathVariable Long tagId, Authentication auth) {
        documentService.removeTagFromDocument(id, tagId, getUserId(auth));
        return ResponseEntity.noContent().build();
    }

    // ── Categorías ──────────────────────────────────────────────────────────

    @PatchMapping("/{documentId}/category/{categoryId}")
    public ResponseEntity<Void> assignCategory(
            @PathVariable Long documentId,
            @PathVariable Long categoryId,
            Authentication auth) {
        categoryService.assignCategory(getUserId(auth), documentId, categoryId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{documentId}/category")
    public ResponseEntity<Void> removeCategory(
            @PathVariable Long documentId,
            Authentication auth) {
        categoryService.removeCategory(getUserId(auth), documentId);
        return ResponseEntity.noContent().build();
    }

    // ── CARPETAS Y ELIMINACIÓN ──────────────────────────────────────────────

    @PatchMapping("/{docId}/folder/{folderId}")
    public ResponseEntity<DocumentResponse> moveToFolder(
            @PathVariable Long docId,
            @PathVariable Long folderId,
            Authentication auth) {
        return ResponseEntity.ok(folderService.moveToFolder(getUserId(auth), docId, folderId));
    }

    @DeleteMapping("/{documentId}/folder")
    public ResponseEntity<DocumentResponse> removeFromFolder(
            @PathVariable Long documentId,
            Authentication auth) {
        return ResponseEntity.ok(folderService.removeFromFolder(getUserId(auth), documentId));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long documentId,
            Authentication auth) {
        documentService.softDelete(getUserId(auth), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable Long documentId,
            Authentication auth) {
        return ResponseEntity.ok(documentService.getDownloadUrl(getUserId(auth), documentId));
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<DownloadUrlResponse> getPreviewUrl(
            @PathVariable Long documentId,
            Authentication auth) {
        return ResponseEntity.ok(documentService.getPreviewUrl(getUserId(auth), documentId));
    }

    // ── ALMACENAMIENTO ──────────────────────────────────────────────────────

    @GetMapping("/storage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStorageUsed(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        long usedBytes = documentService.getStorageUsedByUser(userDetails.getId());
        return ResponseEntity.ok(Map.of("usedBytes", usedBytes));
    }
}