package com.docucloud.backend.favorites.repository;

import com.docucloud.backend.favorites.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // ─── QUERIES SIMPLES ──────────────────────────────────────────────────────

    Optional<Favorite> findByUserIdAndDocumentId(Long userId, Long documentId);

    boolean existsByUserIdAndDocumentId(Long userId, Long documentId);

    long countByUserId(Long userId);

    // ─── PANEL COMPLETO ───────────────────────────────────────────────────────

    /**
     * CA25.3 – Favoritos del usuario ordenados por fecha de marcado DESC.
     * JOIN FETCH evita N+1 al acceder a f.document dentro del mapper.
     */
    @Query("""
    SELECT f FROM Favorite f
    WHERE f.user.id = :userId
    ORDER BY f.createdAt DESC
""")
    List<Favorite> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    // ─── BATCH LOOKUP (anti N+1) ──────────────────────────────────────────────

    /**
     * RF-25 – Recibe los IDs de la página actual y retorna solo los que
     * son favoritos del usuario. Una sola query en lugar de N existsBy...
     */
    @Query("""
        SELECT f.document.id FROM Favorite f
        WHERE f.user.id = :userId
        AND f.document.id IN :documentIds
    """)
    Set<Long> findFavoriteDocumentIds(
            @Param("userId") Long userId,
            @Param("documentIds") Set<Long> documentIds
    );

    // ─── PENDIENTE RF-17/18 ───────────────────────────────────────────────────
    // findByUserIdAndCategoryId se agregará cuando Document tenga
    // la relación @ManyToMany categories (sprint de clasificación inteligente).
}
