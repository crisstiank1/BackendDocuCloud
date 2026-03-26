package com.docucloud.backend.favorites.service;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.favorites.dto.response.FavoriteResponse;
import com.docucloud.backend.favorites.model.Favorite;
import com.docucloud.backend.favorites.repository.FavoriteRepository;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository     userRepository;
    private final AuditService       auditService;

    @Value("${docucloud.favorites.max-per-user:50}")
    private int maxFavoritesPerUser;

    // ─── TOGGLE ───────────────────────────────────────────────────────────────

    @Transactional
    public boolean toggleFavorite(Long userId, Long documentId) {
        Optional<Favorite> existing =
                favoriteRepository.findByUserIdAndDocumentId(userId, documentId);

        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            log.info("⭐ Unfavorited - user={} doc={}", userId, documentId);

            String docName = "—";
            try {
                docName = documentRepository.findById(documentId)
                        .map(Document::getFileName)
                        .orElse("—");
            } catch (Exception ignored) {}

            ObjectNode details = JsonNodeFactory.instance.objectNode();
            details.put("name", docName);
            auditService.logBusiness(userId, "FAVORITE_REMOVE", "Document", documentId, true, details);

            return false;
        }

        long count = favoriteRepository.countByUserId(userId);
        if (count >= maxFavoritesPerUser) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Has alcanzado el límite de " + maxFavoritesPerUser + " favoritos. " +
                            "Elimina alguno para agregar uno nuevo."
            );
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        if (!document.getOwnerUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tienes permiso sobre este documento");
        }

        User user = userRepository.getReferenceById(userId);

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setDocument(document);
        favoriteRepository.save(favorite);

        log.info("⭐ Favorited - user={} doc={}", userId, documentId);

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("name", document.getFileName());
        auditService.logBusiness(userId, "FAVORITE_ADD", "Document", documentId, true, details);

        return true;
    }

    // ─── PANEL DE FAVORITOS ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavorites(Long userId, Long categoryId) {
        List<Favorite> favorites =
                favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return favorites.stream()
                .map(this::toResponse)
                .toList();
    }

    // ─── UTILIDADES PARA DocumentService ─────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long documentId) {
        return favoriteRepository.existsByUserIdAndDocumentId(userId, documentId);
    }

    /**
     * Batch lookup — retorna los IDs del set dado que son favoritos del usuario.
     * Usado por DocumentService para evitar N+1 al paginar listas.
     * Delega la query al repositorio con IN clause.
     */
    @Transactional(readOnly = true)
    public Set<Long> getFavoriteIdsByDocumentIds(Long userId, Set<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return Set.of();
        return favoriteRepository.findFavoriteDocumentIds(userId, documentIds);
    }

    // ─── MAPPER ───────────────────────────────────────────────────────────────

    private FavoriteResponse toResponse(Favorite f) {
        Document doc = f.getDocument();
        log.debug("Mapping favorite: docId={}", doc.getId());   // debug, no info

        return FavoriteResponse.builder()
                .documentId(doc.getId())
                .documentName(doc.getFileName() != null ? doc.getFileName() : "sin nombre")
                .fileType(doc.getMimeType()   != null ? doc.getMimeType()   : "unknown")
                .sizeBytes(doc.getSizeBytes())
                .folderId(doc.getFolderId())
                .folderName(null)
                .favoritedAt(f.getCreatedAt())
                .categoryNames(List.of())
                .build();
    }
}