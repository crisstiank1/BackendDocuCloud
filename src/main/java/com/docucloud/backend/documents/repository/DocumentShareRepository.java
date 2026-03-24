package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.DocumentShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentShareRepository extends JpaRepository<DocumentShare, UUID> {

    Optional<DocumentShare> findByIdAndRevokedFalse(UUID id);

    List<DocumentShare> findByDocumentIdAndRevokedFalse(Long documentId);

    Optional<DocumentShare> findByIdAndSharedByUserId(UUID id, Long sharedByUserId);

    Page<DocumentShare> findBySharedByUserIdOrderByCreatedAtDesc(Long sharedByUserId, Pageable pageable);

    Page<DocumentShare> findBySharedByUserIdAndRevokedFalseOrderByCreatedAtDesc(
            Long sharedByUserId, Pageable pageable);

    Page<DocumentShare> findByRecipientEmailAndRevokedFalseOrderByCreatedAtDesc(
            String recipientEmail, Pageable pageable);

    Optional<DocumentShare> findByIdAndRecipientEmailAndRevokedFalse(UUID id, String recipientEmail);

    List<DocumentShare> findByDocumentIdAndSharedByUserIdAndRevokedFalse(
            Long documentId,
            Long sharedByUserId
    );

    // solo shares cuyo documento NO está eliminado
    @Query("SELECT s FROM DocumentShare s " +
            "JOIN Document d ON d.id = s.documentId " +
            "WHERE s.recipientEmail = :email " +
            "AND s.revoked = false " +
            "AND d.deletedAt IS NULL " +
            "ORDER BY s.createdAt DESC")
    Page<DocumentShare> findActiveSharesWithAvailableDocuments(
            @Param("email") String email, Pageable pageable);
}