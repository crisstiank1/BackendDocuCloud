package com.docucloud.backend.auth.repository;

import com.docucloud.backend.auth.model.RefreshToken;
import com.docucloud.backend.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    long deleteByUser(User user);
}
