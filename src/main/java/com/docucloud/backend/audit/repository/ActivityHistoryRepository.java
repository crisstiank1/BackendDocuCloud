package com.docucloud.backend.audit.repository;

import com.docucloud.backend.audit.model.ActivityHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ActivityHistoryRepository
        extends JpaRepository<ActivityHistory, Integer>,
        JpaSpecificationExecutor<ActivityHistory> {

    long countByIsSuccessfulFalse();

    @Query("SELECT COUNT(DISTINCT a.userId) FROM ActivityHistory a WHERE a.userId IS NOT NULL")
    long countDistinctUserId();

    List<ActivityHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ActivityHistory> findByActionAndCreatedAtBetween(String action, Instant from, Instant to);

    List<ActivityHistory> findByIsSuccessfulFalseOrderByCreatedAtDesc();

    Page<ActivityHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

