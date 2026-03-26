package com.docucloud.backend.audit.repository;

import com.docucloud.backend.audit.model.ActivityHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityHistoryRepository
        extends JpaRepository<ActivityHistory, Integer>,
        JpaSpecificationExecutor<ActivityHistory> {

    long countByIsSuccessfulFalse();

    @Query("SELECT COUNT(DISTINCT a.userId) FROM ActivityHistory a WHERE a.userId IS NOT NULL")
    long countDistinctUserId();
}