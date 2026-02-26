package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

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

    // BÚSQUEDA AVANZADA (RF-11, RF-21, RF-22)
    @Query("""
        select d from Document d
        where d.ownerUserId = :ownerId
          and d.deletedAt is null
          and (:nameQuery is null or lower(d.fileName) like :nameQuery)
          and (:mimeType is null or d.mimeType = :mimeType)
          and (:status is null or d.status = :status)
          and (:fromDate is null or d.createdAt >= :fromDate)
          and (:toDate is null or d.createdAt <= :toDate)
        order by 
          case when :nameQuery is not null then 
            case when lower(d.fileName) like :nameQuery then 0 else 1 end 
          end,
          d.createdAt desc
    """)
    Page<Document> searchDocuments(
            @Param("ownerId") Long ownerId,
            @Param("nameQuery") String nameQuery,
            @Param("mimeType") String mimeType,
            @Param("status") DocumentStatus status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable);


    // Búsqueda solo por nombre (rápida)
    Page<Document> findByOwnerUserIdAndDeletedAtIsNullAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Long ownerUserId, String nameQuery, Pageable pageable);

    // Por tipo MIME
    Page<Document> findByOwnerUserIdAndMimeTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, String mimeType, Pageable pageable);

    // Por rango de fechas
    Page<Document> findByOwnerUserIdAndCreatedAtBetweenAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long ownerUserId, Instant from, Instant to, Pageable pageable);

    // Contar documentos por usuario (útil para dashboard)
    long countByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId);

    // Documentos por status (útil para analytics)
    long countByOwnerUserIdAndStatusAndDeletedAtIsNull(Long ownerUserId, DocumentStatus status);
}
