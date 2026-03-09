package com.docucloud.backend.searchhistory.repository;

import com.docucloud.backend.searchhistory.model.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    List<SearchHistory> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    List<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SearchHistory> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
