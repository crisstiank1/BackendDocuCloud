package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentShareRepository extends JpaRepository<DocumentShare, UUID> {

    // Buscar share activo por ID (no revocado)
    Optional<DocumentShare> findByIdAndRevokedFalse(UUID id);


    // Listar todos los shares activos de un documento
    List<DocumentShare> findByDocumentIdAndRevokedFalse(Long documentId);

    // Verificar si un usuario es dueño del share
    Optional<DocumentShare> findByIdAndSharedByUserId(UUID id, Long sharedByUserId);
}

