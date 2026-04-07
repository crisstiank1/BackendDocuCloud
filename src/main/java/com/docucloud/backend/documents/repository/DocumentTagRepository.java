package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.DocumentTag;
import com.docucloud.backend.documents.model.DocumentTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, DocumentTagId> {

    // Listar tags de un documento
    List<DocumentTag> findByDocumentId(Long documentId);

    // Eliminar relación específica
    void deleteByDocumentIdAndTagId(Long documentId, Long tagId);

    // Verificar si existe relación (para addTag idempotente)
    Optional<DocumentTag> findByDocumentIdAndTagId(Long documentId, Long tagId);

    // NUEVO — batch: todos los tags de múltiples documentos en 1 query
    List<DocumentTag> findByDocumentIdIn(List<Long> documentIds);
}