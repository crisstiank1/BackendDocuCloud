package com.docucloud.backend.audit.repository;

import com.docucloud.backend.audit.model.ActivityHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ActivityHistoryRepository extends JpaRepository<ActivityHistory, Integer> {

    List<ActivityHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ActivityHistory> findByActionAndCreatedAtBetween(String action, Instant from, Instant to);

    List<ActivityHistory> findByIsSuccessfulFalseOrderByCreatedAtDesc();
}
