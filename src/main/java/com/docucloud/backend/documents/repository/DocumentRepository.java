package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>,
        JpaSpecificationExecutor<Document> {

    // ─── LISTADO ──────────────────────────────────────────────────────────────

    Page<Document> findAllByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId, Pageable pageable);

    Optional<Document> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerUserId);

    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    Page<Document> findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, DocumentStatus status, Pageable pageable);

    // ─── CARPETAS ─────────────────────────────────────────────────────────────

    Page<Document> findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(
            Long ownerUserId, Long folderId, Pageable pageable);

    Page<Document> findByOwnerUserIdAndFolderIdIsNullAndDeletedAtIsNull(
            Long ownerUserId, Pageable pageable);

    // ─── BÚSQUEDA ─────────────────────────────────────────────────────────────

    Page<Document> findByOwnerUserIdAndDeletedAtIsNullAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Long ownerUserId, String nameQuery, Pageable pageable);

    Page<Document> findByOwnerUserIdAndMimeTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, String mimeType, Pageable pageable);

    Page<Document> findByOwnerUserIdAndCreatedAtBetweenAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, Instant from, Instant to, Pageable pageable);

    // ─── CONTEOS ──────────────────────────────────────────────────────────────

    long countByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId);

    long countByOwnerUserIdAndStatusAndDeletedAtIsNull(Long ownerUserId, DocumentStatus status);

    // ─── STORAGE ──────────────────────────────────────────────────────────────

    // ✅ Agregación en BD — reemplaza findByOwnerUserIdAndStatusNotAndDeletedAtIsNull
    @Query("""
            SELECT COALESCE(SUM(d.sizeBytes), 0) FROM Document d
            WHERE d.ownerUserId = :userId
              AND d.status != com.docucloud.backend.documents.model.DocumentStatus.DELETED
              AND d.deletedAt IS NULL
            """)
    long sumStorageByUser(@Param("userId") Long userId);

    // ─── CATEGORÍAS ───────────────────────────────────────────────────────────

    @Query("""
            SELECT d FROM Document d
            WHERE d.ownerUserId = :ownerUserId
              AND d.deletedAt IS NULL
              AND d.classification.category.id = :categoryId
            """)
    Page<Document> findByOwnerUserIdAndCategoryId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("categoryId") Long categoryId,
            Pageable pageable);

    @Query("""
            SELECT d FROM Document d
            WHERE d.ownerUserId = :ownerUserId
              AND d.deletedAt IS NULL
              AND d.status != com.docucloud.backend.documents.model.DocumentStatus.DELETED
              AND d.classification IS NULL
            """)
    Page<Document> findUnclassifiedByOwnerUserId(
            @Param("ownerUserId") Long ownerUserId,
            Pageable pageable);

    // ─── COMPARTIDOS ──────────────────────────────────────────────────────────

    @Query(
            value = """
            SELECT DISTINCT d FROM Document d
            WHERE d.ownerUserId = :userId
              AND d.deletedAt IS NULL
              AND d.status != com.docucloud.backend.documents.model.DocumentStatus.DELETED
              AND EXISTS (
                  SELECT 1 FROM DocumentShare s
                  WHERE s.documentId = d.id
                    AND s.sharedByUserId = :userId
                    AND s.revoked = false
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT d) FROM Document d
            WHERE d.ownerUserId = :userId
              AND d.deletedAt IS NULL
              AND d.status != com.docucloud.backend.documents.model.DocumentStatus.DELETED
              AND EXISTS (
                  SELECT 1 FROM DocumentShare s
                  WHERE s.documentId = d.id
                    AND s.sharedByUserId = :userId
                    AND s.revoked = false
              )
            """
    )
    Page<Document> findSharedByMe(@Param("userId") Long userId, Pageable pageable);

    // soft delete masivo — marca todos los docs del usuario como eliminados
    @Modifying
    @Query("UPDATE Document d SET d.deletedAt = :now WHERE d.ownerUserId = :userId AND d.deletedAt IS NULL")
    void softDeleteByOwnerUserId(@Param("userId") Long userId, @Param("now") Instant now);
}