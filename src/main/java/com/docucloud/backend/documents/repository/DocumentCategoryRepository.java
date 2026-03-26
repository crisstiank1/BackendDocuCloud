package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCategoryRepository
        extends JpaRepository<DocumentCategory, Long> {

    // Todas las clasificaciones de un documento
    List<DocumentCategory> findByDocument_Id(Long documentId);

    // Cuántas clasificaciones tiene una categoría
    long countByCategory_IdAndIsAutomaticallyAssignedTrue(Long categoryId);

    // Eliminar clasificación de un documento
    void deleteByDocument_Id(Long documentId);

    // Eliminar todas las clasificaciones de una categoría ✅ nuevo
    void deleteByCategory_Id(Long categoryId);

    // Para desasociar docs cuando se borra una categoría
    List<DocumentCategory> findByCategory_IdAndDocument_OwnerUserIdAndDocument_DeletedAtIsNull(
            Long categoryId, Long ownerUserId);

    // borra clasificaciones de múltiples categorías en 1 query
    void deleteByCategory_IdIn(List<Long> categoryIds);
}