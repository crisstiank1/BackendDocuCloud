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

    @Query("""
        SELECT COUNT(d) FROM Document d
        WHERE d.ownerUserId = :userId
          AND d.categoryId = :categoryId
          AND d.deletedAt IS NULL
        """)
    long countDocumentsByCategoryId(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId
    );
}
