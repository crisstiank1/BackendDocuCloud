package com.docucloud.backend.favorites.controller;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.favorites.dto.response.FavoriteResponse;
import com.docucloud.backend.favorites.service.FavoriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/favorites/{documentId}
     * Toggle favorito — audita con acción diferente según el resultado:
     *   marked=true  → FAVORITE_ADD
     *   marked=false → FAVORITE_REMOVE
     * Así el dashboard puede mostrar mensajes distintos para cada caso.
     */
    @PostMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> toggleFavorite(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        boolean marked = favoriteService.toggleFavorite(userDetails.getId(), documentId);

        // Auditoría directa: la acción depende del resultado del toggle
        String action = marked ? "FAVORITE_ADD" : "FAVORITE_REMOVE";
        ObjectNode details = objectMapper.createObjectNode();
        details.put("documentId", documentId);
        auditService.logBusiness(
                userDetails.getId(),
                action,
                "Document",
                documentId,
                true,
                details
        );

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "isFavorite", marked,
                "message", marked ? "Agregado a favoritos" : "Eliminado de favoritos"
        ));
    }

    /**
     * GET /api/favorites
     * Panel de favoritos — CU-25b1 (todos) · CU-25b2 (filtrado por categoría)
     */
    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> getFavorites(
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        return ResponseEntity.ok(
                favoriteService.getFavorites(userDetails.getId(), categoryId)
        );
    }
}