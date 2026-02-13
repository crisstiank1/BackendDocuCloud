package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findAllByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId, Pageable pageable);

    Optional<Document> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerUserId);
}
