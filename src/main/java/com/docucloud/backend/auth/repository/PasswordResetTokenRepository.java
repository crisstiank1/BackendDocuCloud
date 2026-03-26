package com.docucloud.backend.auth.repository;

import com.docucloud.backend.auth.model.PasswordResetToken;
import com.docucloud.backend.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    long deleteByExpiresAtBefore(Instant cutoff);
    // Fix #3: invalida tokens pendientes anteriores del mismo usuario
    void deleteByUserAndUsedAtIsNull(User user);
    void deleteByUser_Id(Long userId);
}
