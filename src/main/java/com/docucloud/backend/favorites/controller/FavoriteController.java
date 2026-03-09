package com.docucloud.backend.favorites.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.favorites.dto.response.FavoriteResponse;
import com.docucloud.backend.favorites.service.FavoriteService;
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

    /**
     * POST /api/favorites/{documentId}
     * Toggle favorito – CU-25a1 (desde lista) · CU-25a2 (desde detalle)
     */
    @PostMapping("/{documentId}")
    public ResponseEntity<Map<String, Object>> toggleFavorite(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        boolean marked = favoriteService.toggleFavorite(userDetails.getId(), documentId);

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "isFavorite", marked,
                "message", marked ? "Agregado a favoritos" : "Eliminado de favoritos"
        ));
    }

    /**
     * GET /api/favorites
     * Panel de favoritos – CU-25b1 (todos) · CU-25b2 (filtrado por categoría)
     * Ordenados por fecha de marcado DESC – CA25.3
     *
     * @param categoryId opcional – filtra por categoría
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
