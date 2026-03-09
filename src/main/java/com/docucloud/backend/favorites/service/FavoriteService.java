package com.docucloud.backend.favorites.service;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.favorites.dto.response.FavoriteResponse;
import com.docucloud.backend.favorites.model.Favorite;
import com.docucloud.backend.favorites.repository.FavoriteRepository;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
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

    @Value("${docucloud.favorites.max-per-user:50}")
    private int maxFavoritesPerUser;

    // ─── TOGGLE ───────────────────────────────────────────────────────────────

    /**
     * Alterna el estado de favorito.
     * @return true si quedó marcado, false si fue desmarcado
     */
    @Transactional
    public boolean toggleFavorite(Long userId, Long documentId) {

        Optional<Favorite> existing =
                favoriteRepository.findByUserIdAndDocumentId(userId, documentId);

        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }

        // Verificar límite – anticipa RF-27
        long count = favoriteRepository.countByUserId(userId);
        if (count >= maxFavoritesPerUser) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Has alcanzado el límite de " + maxFavoritesPerUser + " favoritos. " +
                            "Elimina alguno para agregar uno nuevo."
            );
        }

        // Verificar que el documento existe y pertenece al usuario
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
        return true;
    }

    // ─── PANEL DE FAVORITOS ───────────────────────────────────────────────────

    /**
     * CA25.2 / CA25.3 – Panel de favoritos ordenado por fecha DESC.
     * categoryId ignorado hasta que se implemente RF-17/18 (clasificación).
     * Cuando Document tenga la relación categories, activar filtro aquí.
     */
    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavorites(Long userId, Long categoryId) {
        // TODO RF-17/18: cuando exista Document.categories, delegar en
        //   favoriteRepository.findByUserIdAndCategoryId(userId, categoryId)
        List<Favorite> favorites =
                favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return favorites.stream()
                .map(this::toResponse)
                .toList();
    }

    // ─── UTILIDADES PARA DocumentService ─────────────────────────────────────

    /**
     * Verifica si un documento es favorito del usuario.
     */
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long documentId) {
        return favoriteRepository.existsByUserIdAndDocumentId(userId, documentId);
    }

    /**
     * Batch lookup – retorna qué IDs del set dado son favoritos del usuario.
     * Usado por DocumentService para evitar N+1 al paginar listas.
     */
    @Transactional(readOnly = true)
    public Set<Long> getFavoriteIdsByDocumentIds(Long userId, Set<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return Set.of();
        return favoriteRepository.findFavoriteDocumentIds(userId, documentIds);
    }

    // ─── MAPPER ───────────────────────────────────────────────────────────────

    private FavoriteResponse toResponse(Favorite f) {
        try {
            Document doc = f.getDocument();

            log.info("🔍 doc fields: id={}, fileName={}, mimeType={}, folderId={}",
                    doc.getId(),
                    doc.getFileName(),
                    doc.getMimeType(),
                    doc.getFolderId());

            return FavoriteResponse.builder()
                    .documentId(doc.getId())
                    .documentName(doc.getFileName() != null ? doc.getFileName() : "sin nombre")
                    .fileType(doc.getMimeType() != null ? doc.getMimeType() : "unknown")
                    .folderId(doc.getFolderId())
                    .folderName(null)
                    .favoritedAt(f.getCreatedAt())
                    .categoryNames(List.of())
                    .build();

        } catch (Exception e) {
            log.error("❌ Error mapping Favorite: {}", e.getMessage(), e);
            throw e;
        }
    }

}
