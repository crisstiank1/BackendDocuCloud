package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCategoryRepository
        extends JpaRepository<DocumentCategory, Long> {  // ← Long, ya no DocumentCategoryId

    // Todas las clasificaciones de un documento
    List<DocumentCategory> findByDocument_Id(Long documentId);  // ← era findByIdDocumentId

    // Cuántas clasificaciones tiene una categoría
    long countByCategory_IdAndIsAutomaticallyAssignedTrue(Long categoryId);  // ← era findByIdCategoryId

    // Eliminar clasificación de un documento
    void deleteByDocument_Id(Long documentId);  // ← era deleteByIdDocumentId

    // Para desasociar docs cuando se borra una categoría (nuevo)
    List<DocumentCategory> findByCategory_IdAndDocument_OwnerUserIdAndDocument_DeletedAtIsNull(
            Long categoryId, Long ownerUserId);
}