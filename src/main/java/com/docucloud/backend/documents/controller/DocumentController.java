package com.docucloud.backend.documents.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.documents.dto.request.*;
import com.docucloud.backend.documents.dto.response.*;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.service.CategoryService;
import com.docucloud.backend.documents.service.DocumentService;
import com.docucloud.backend.documents.service.FolderService;
import com.docucloud.backend.documents.service.ShareService;
import com.docucloud.backend.search.service.SearchHistoryService;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.tags.dto.response.TagResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    // ── UPLOAD ───────────────────────────────────────────────────────────────

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

    // ── LISTADO Y BÚSQUEDA ────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "false") boolean unclassified,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {

        Long userId = getUserId(auth);

        if (unclassified) {
            return ResponseEntity.ok(documentService.listUnclassified(userId, pageable));
        }
        if (categoryId != null) {
            return ResponseEntity.ok(
                    documentService.listWithFavoritesByCategory(userId, categoryId, pageable));
        }
        return ResponseEntity.ok(documentService.listWithFavorites(userId, pageable));
    }

    @GetMapping("/recent")
    public ResponseEntity<Page<DocumentResponse>> recentDocuments(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(
                documentService.getRecentDocumentsWithFavorites(getUserId(auth), pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DocumentResponse>> searchDocuments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String mimeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {

        Long userId = getUserId(auth);
        if (query != null && !query.trim().isEmpty()) {
            searchHistoryService.saveSearch(userId, query);
        }
        return ResponseEntity.ok(documentService.searchWithFavorites(
                userId, query, mimeType, status, fromDate, toDate, pageable));
    }

    // ── COMPARTIR (SHARE) ─────────────────────────────────────────────────────

    // Crear share
    @PutMapping("/{docId}/share")
    public ResponseEntity<ShareResponse> shareDocument(
            @PathVariable Long docId,
            @Valid @RequestBody ShareRequest request,
            Authentication auth) {
        return ResponseEntity.ok(shareService.shareDocument(docId, request, getUserId(auth)));
    }

    // Shares de un documento específico
    @GetMapping("/{docId}/shares")
    public ResponseEntity<List<ShareSummaryResponse>> getDocumentShares(
            @PathVariable Long docId,
            Authentication auth) {
        return ResponseEntity.ok(shareService.getDocumentShares(docId, getUserId(auth)));
    }

    // ── Rutas estáticas primero ───────────────────────────────────────────────

    // Mis shares (lista plana)
    @GetMapping("/shares/mine")
    public ResponseEntity<Page<ShareSummaryResponse>> getMyShares(
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(
                shareService.getMyShares(getUserId(auth), includeRevoked, pageable));
    }

    // Compartidos por mí (agrupado por documento) — NUEVO
    @GetMapping("/shared-by-me")
    public ResponseEntity<Page<SharedByMeResponse>> getSharedByMe(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(
                shareService.getSharedByMe(getUserId(auth), pageable));
    }

    // Compartidos conmigo
    @GetMapping("/shares/received")
    public ResponseEntity<Page<SharedWithMeResponse>> getSharedWithMe(
            @PageableDefault(size = 50) Pageable pageable,
            Authentication auth) {
        UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();
        return ResponseEntity.ok(shareService.getSharedWithMe(user.getEmail(), pageable));
    }

    // ── Rutas dinámicas después ───────────────────────────────────────────────

    // Acceder a un share por enlace
    @GetMapping("/shares/{shareId}/access")
    public ResponseEntity<ShareAccessResponse> accessShare(
            @PathVariable UUID shareId,
            @RequestParam(required = false) String password) {
        return ResponseEntity.ok(shareService.accessShare(shareId, password));
    }

    // Revocar share
    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<Void> revokeShare(
            @PathVariable UUID shareId,
            Authentication auth) {
        shareService.revokeShare(shareId, getUserId(auth));
        return ResponseEntity.noContent().build();
    }

    // Actualizar permiso de un share
    @PatchMapping("/shares/{shareId}/permission")
    public ResponseEntity<ShareResponse> updateSharePermission(
            @PathVariable UUID shareId,
            @Valid @RequestBody UpdateSharePermissionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                shareService.updateSharePermission(
                        shareId, request.getPermission(), getUserId(auth)));
    }

    // ── SHARES RECIBIDOS ──────────────────────────────────────────────────────

    // El destinatario elimina su propio acceso
    @DeleteMapping("/shares/received/{shareId}")
    public ResponseEntity<Void> removeFromSharedWithMe(
            @PathVariable UUID shareId,
            Authentication auth) {
        UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();
        shareService.removeSharedWithMe(shareId, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    // URL de escritura para destinatario con permiso WRITE
    @GetMapping("/shares/received/{shareId}/write-url")
    public ResponseEntity<PresignedUrlResponse> getWriteUrlForRecipient(
            @PathVariable UUID shareId,
            @RequestParam String mimeType,
            Authentication auth) {
        UserDetailsImpl user = (UserDetailsImpl) auth.getPrincipal();
        return ResponseEntity.ok(
                shareService.getWriteUrlForRecipient(shareId, user.getEmail(), mimeType));
    }

    // ── TAGS ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/tags")
    public List<TagResponse> getDocumentTags(
            @PathVariable Long id, Authentication auth) {
        return documentService.getDocumentTags(id, getUserId(auth));
    }

    @PutMapping("/{id}/tags/{tagId}")
    public ResponseEntity<Void> addTag(
            @PathVariable Long id,
            @PathVariable Long tagId,
            Authentication auth) {
        documentService.addTagToDocument(id, tagId, getUserId(auth));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public ResponseEntity<Void> removeTag(
            @PathVariable Long id,
            @PathVariable Long tagId,
            Authentication auth) {
        documentService.removeTagFromDocument(id, tagId, getUserId(auth));
        return ResponseEntity.noContent().build();
    }

    // ── CATEGORÍAS ────────────────────────────────────────────────────────────────

    @PutMapping("/{documentId}/category")
    public ResponseEntity<Void> assignCategory(
            @PathVariable Long documentId,
            @RequestBody AssignCategoryRequest request,
            Authentication auth) {
        Long userId = getUserId(auth);
        if (request.categoryId() == null) {
            categoryService.removeCategory(userId, documentId);
        } else {
            categoryService.assignCategory(userId, documentId, request.categoryId());
        }
        return ResponseEntity.ok().build();
    }

    // ── CARPETAS Y ELIMINACIÓN ────────────────────────────────────────────────

    @PatchMapping("/{docId}/folder/{folderId}")
    public ResponseEntity<DocumentResponse> moveToFolder(
            @PathVariable Long docId,
            @PathVariable Long folderId,
            Authentication auth) {
        return ResponseEntity.ok(
                folderService.moveToFolder(getUserId(auth), docId, folderId));
    }

    @DeleteMapping("/{documentId}/folder")
    public ResponseEntity<DocumentResponse> removeFromFolder(
            @PathVariable Long documentId,
            Authentication auth) {
        return ResponseEntity.ok(
                folderService.removeFromFolder(getUserId(auth), documentId));
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
        return ResponseEntity.ok(
                documentService.getDownloadUrl(getUserId(auth), documentId));
    }

    @GetMapping("/{documentId}/preview")
    public ResponseEntity<DownloadUrlResponse> getPreviewUrl(
            @PathVariable Long documentId,
            Authentication auth) {
        return ResponseEntity.ok(
                documentService.getPreviewUrl(getUserId(auth), documentId));
    }

    // ── ALMACENAMIENTO ────────────────────────────────────────────────────────

    @GetMapping("/storage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getStorageUsed(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        long usedBytes = documentService.getStorageUsedByUser(userDetails.getId());
        return ResponseEntity.ok(Map.of("usedBytes", usedBytes));
    }

    // ── STREAM PARA GOOGLE DOCS VIEWER ────────────────────────────────────────

    @GetMapping("/{id}/stream")
    public ResponseEntity<byte[]> streamDocument(@PathVariable Long id) {
        try {
            Document document = documentService.getDocumentByIdPublic(id);
            byte[] fileBytes = documentService.downloadFileBytes(document);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + document.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(document.getMimeType()))
                    .body(fileBytes);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}