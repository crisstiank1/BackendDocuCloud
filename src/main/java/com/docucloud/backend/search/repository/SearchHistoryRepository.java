package com.docucloud.backend.search.repository;

import com.docucloud.backend.search.model.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    @Query("SELECT s FROM SearchHistory s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<SearchHistory> findByUser_IdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT s FROM SearchHistory s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<SearchHistory> findTop10ByUser_IdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("""
    SELECT DISTINCT s
    FROM SearchHistory s
    WHERE s.user.id = :userId
    AND LOWER(s.query) LIKE LOWER(CONCAT(:prefix, '%'))
    ORDER BY s.createdAt DESC
    """)
    List<SearchHistory> findSmartSuggestions(@Param("userId") Long userId, @Param("prefix") String prefix);

    Optional<SearchHistory> findByIdAndUser_Id(Long id, Long userId);

    void deleteByUser_Id(Long userId);

    //Sugerencias autocompletado
    @Query("SELECT DISTINCT s FROM SearchHistory s " +
            "WHERE s.user.id = :userId AND LOWER(s.query) LIKE LOWER(:prefix%) " +
            "ORDER BY s.createdAt DESC LIMIT 5")
    List<SearchHistory> findSuggestionsByUser(@Param("userId") Long userId,
                                              @Param("prefix") String prefix);
}
