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

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> getFavorites(
            @RequestParam(required = false) Long categoryId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        return ResponseEntity.ok(
                favoriteService.getFavorites(userDetails.getId(), categoryId)
        );
    }
}