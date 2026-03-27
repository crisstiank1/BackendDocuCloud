package com.docucloud.backend.documents.repository;

import com.docucloud.backend.documents.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);

    Optional<Category> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    Optional<Category> findByOwnerUserIdAndName(Long ownerUserId, String name);

    boolean existsByOwnerUserIdAndName(Long ownerUserId, String name);

    long countByOwnerUserId(Long userId);

    @Query("""
    SELECT COUNT(dc) FROM DocumentCategory dc
    WHERE dc.document.ownerUserId = :userId
      AND dc.category.id = :categoryId
      AND dc.document.deletedAt IS NULL
    """)
    long countDocumentsByCategoryId(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId);


    // CategoryRepository.java
    @Query("""
        SELECT dc.category.id, COUNT(dc)
        FROM DocumentCategory dc
        WHERE dc.category.ownerUserId = :userId
          AND dc.document.deletedAt IS NULL
        GROUP BY dc.category.id
        """)
    List<Object[]> countDocumentsGroupedByCategory(@Param("userId") Long userId);

    List<Category> findByOwnerUserId(Long ownerUserId);
}
