package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.DocumentCategory;
import com.docucloud.backend.documents.model.DocumentCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCategoryRepository
        extends JpaRepository<DocumentCategory, DocumentCategoryId> {

    // Todas las clasificaciones AI de un documento
    List<DocumentCategory> findByIdDocumentId(Long documentId);

    // Cuántas clasificaciones AI tiene una categoría
    long countByIdCategoryIdAndIsAutomaticallyAssignedTrue(Long categoryId);

    // Eliminar todas las clasificaciones de un documento (útil en softDelete)
    void deleteByIdDocumentId(Long documentId);
}
