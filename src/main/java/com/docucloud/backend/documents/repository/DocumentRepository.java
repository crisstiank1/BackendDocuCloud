package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long>,
        JpaSpecificationExecutor<Document> {

    // Listar todos del usuario
    Page<Document> findAllByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId, Pageable pageable);

    // Buscar por ID + permisos
    Optional<Document> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerUserId);

    // Recientes (status AVAILABLE)
    Page<Document> findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, DocumentStatus status, Pageable pageable);

    // Necesario para accessShare() sin filtro de owner
    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    // Documentos de una carpeta
    Page<Document> findByOwnerUserIdAndFolderIdAndDeletedAtIsNull(
            Long ownerUserId, Long folderId, Pageable pageable);

    // Documentos sin carpeta
    Page<Document> findByOwnerUserIdAndFolderIdIsNullAndDeletedAtIsNull(
            Long ownerUserId, Pageable pageable);

    // Búsqueda solo por nombre (rápida)
    Page<Document> findByOwnerUserIdAndDeletedAtIsNullAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Long ownerUserId, String nameQuery, Pageable pageable);

    // Por tipo MIME
    Page<Document> findByOwnerUserIdAndMimeTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, String mimeType, Pageable pageable);

    // Por rango de fechas
    Page<Document> findByOwnerUserIdAndCreatedAtBetweenAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, Instant from, Instant to, Pageable pageable);

    // Contar documentos por usuario
    long countByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId);

    // Documentos por status
    long countByOwnerUserIdAndStatusAndDeletedAtIsNull(Long ownerUserId, DocumentStatus status);

    List<Document> findByOwnerUserIdAndStatusNotAndDeletedAtIsNull(
            Long ownerUserId,
            DocumentStatus status
    );

    // Documentos por categoría
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

    // Documentos sin categoría asignada
    @Query("""
            SELECT d FROM Document d
            WHERE d.ownerUserId = :ownerUserId
              AND d.deletedAt IS NULL
              AND d.status != 'DELETED'
              AND d.classification IS NULL
            """)
    Page<Document> findUnclassifiedByOwnerUserId(
            @Param("ownerUserId") Long ownerUserId,
            Pageable pageable);

    // Documentos que el usuario ha compartido con al menos un share activo
    @Query(
            value = """
            SELECT DISTINCT d FROM Document d
            WHERE d.ownerUserId = :userId
              AND d.deletedAt IS NULL
              AND d.status != 'DELETED'
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
              AND d.status != 'DELETED'
              AND EXISTS (
                  SELECT 1 FROM DocumentShare s
                  WHERE s.documentId = d.id
                    AND s.sharedByUserId = :userId
                    AND s.revoked = false
              )
            """
    )
    Page<Document> findSharedByMe(
            @Param("userId") Long userId,
            Pageable pageable
    );
}