package com.docucloud.backend.documents.controller;

import com.docucloud.backend.documents.dto.request.CreateFolderRequest;
import com.docucloud.backend.documents.dto.request.RenameFolderRequest;
import com.docucloud.backend.documents.dto.response.DocumentResponse;
import com.docucloud.backend.documents.dto.response.FolderResponse;
import com.docucloud.backend.documents.service.FolderService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
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

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(folderService.createFolder(getUserId(auth), request));
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> listFolders(Authentication auth) {
        return ResponseEntity.ok(folderService.listFolders(getUserId(auth)));
    }

    @GetMapping("/{folderId}/documents")
    public ResponseEntity<Page<DocumentResponse>> getDocuments(
            @PathVariable Long folderId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(
                folderService.getDocumentsByFolder(getUserId(auth), folderId, pageable));
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable Long folderId,
            @Valid @RequestBody RenameFolderRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                folderService.renameFolder(getUserId(auth), folderId, request));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable Long folderId,
            Authentication auth) {
        folderService.deleteFolder(getUserId(auth), folderId);
        return ResponseEntity.noContent().build();
    }
}
