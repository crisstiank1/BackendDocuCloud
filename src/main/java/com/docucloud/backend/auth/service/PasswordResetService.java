package com.docucloud.backend.auth.service;

import com.docucloud.backend.auth.model.PasswordResetToken;
import com.docucloud.backend.auth.repository.PasswordResetTokenRepository;
import com.docucloud.backend.common.service.EmailService;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordBaseUrl;

    @Value("${app.security.password-reset-token-ttl-minutes:15}")
    private long tokenTtlMinutes;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        var userOpt = userRepository.findByEmail(email);

        // OWASP: respuesta/flujo consistente aunque el usuario no exista (anti-enumeración). [web:329]
        // Generamos token siempre (trabajo similar) pero solo enviamos correo si existe user.
        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        if (userOpt.isEmpty()) return;

        User user = userOpt.get();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(tokenTtlMinutes));

        PasswordResetToken prt = new PasswordResetToken(tokenHash, user, now, expiresAt);
        tokenRepository.save(prt);

        String resetUrl = resetPasswordBaseUrl + "?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256Hex(rawToken);
        PasswordResetToken prt = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido"));

        Instant now = Instant.now();
        if (prt.isUsed() || prt.isExpired(now)) {
            throw new IllegalArgumentException("Token inválido o expirado");
        }

        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(newPassword)); // Spring Security: PasswordEncoder para almacenar seguro. [web:348]
        prt.markUsed(now);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32]; // 256-bit
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo hashear token", e);
        }
    }
}
