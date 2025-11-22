package com.docucloud.backend.service;

import com.docucloud.backend.model.RefreshToken;
import com.docucloud.backend.model.User;
import com.docucloud.backend.repository.RefreshTokenRepository;
import com.docucloud.backend.repository.UserRepository;
import com.docucloud.backend.security.jwt.JwtUtils;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class RefreshTokenService {

    // DTO ligero
    public static record Tokens(String access, String refresh) {}

    @Value("${docucloud.app.jwtRefreshExpirationMs}")
    private long refreshTtlMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtUtils jwtUtils) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public String createRefreshToken(User user) {
        String token = jwtUtils.generateRefreshToken(user.getEmail());
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(token);
        rt.setExpiryDate(Instant.now().plusMillis(refreshTtlMs));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);
        return token;
    }

    @Transactional
    public Optional<Tokens> refreshAccessToken(String refreshToken) {
        return refreshTokenRepository.findByToken(refreshToken)
                // no revocado y no expirado en BD
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpiryDate().isAfter(Instant.now()))
                // token JWT de refresh válido criptográficamente
                .filter(rt -> jwtUtils.validateRefreshToken(rt.getToken()))
                .map(rt -> {
                    // sujeto (email) desde el JWT de refresh
                    String subject = jwtUtils.extractUsername(rt.getToken(), true);

                    // Rotación: revocar el refresh usado
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);

                    // Crear nuevo refresh persistido
                    String newRefresh = jwtUtils.generateRefreshToken(subject);
                    User user = userRepository.findByEmail(subject).orElseThrow(); // no debería fallar
                    RefreshToken nxt = new RefreshToken();
                    nxt.setUser(user);
                    nxt.setToken(newRefresh);
                    nxt.setExpiryDate(Instant.now().plusMillis(refreshTtlMs));
                    nxt.setRevoked(false);
                    refreshTokenRepository.save(nxt);

                    // Nuevo access token solo a partir del subject (email)
                    String newAccess = jwtUtils.generateAccessTokenFromSubject(subject);

                    return new Tokens(newAccess, newRefresh);
                });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
}
