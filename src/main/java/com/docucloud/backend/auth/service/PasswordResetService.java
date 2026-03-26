package com.docucloud.backend.auth.service;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.model.PasswordResetToken;
import com.docucloud.backend.auth.repository.PasswordResetTokenRepository;
import com.docucloud.backend.common.service.EmailService;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordBaseUrl;

    @Value("${app.security.password-reset-token-ttl-minutes:15}")
    private long tokenTtlMinutes;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        log.info("[Reset] Solicitud recibida para: {}", email);

        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("[Reset] Usuario no encontrado para: {}", email);
            return; // anti-enumeración: no revelamos si existe
        }

        User user = userOpt.get();

        tokenRepository.deleteByUserAndUsedAtIsNull(user);
        tokenRepository.flush();

        Instant now = Instant.now();
        PasswordResetToken prt = new PasswordResetToken(
                tokenHash, user, now, now.plus(Duration.ofMinutes(tokenTtlMinutes))
        );
        tokenRepository.save(prt);

        log.info("[Reset] Token guardado en BD para: {}", email);

        auditService.logBusiness(user.getId(), "FORGOT_PASSWORD", "Auth", user.getId(), true, null);

        String resetUrl = resetPasswordBaseUrl + "?token=" + rawToken;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("[Reset] Transacción confirmada, enviando correo a: {}", email);
                emailService.sendPasswordResetEmail(email, resetUrl);
            }
        });
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

        prt.getUser().setPassword(passwordEncoder.encode(newPassword));
        prt.markUsed(now);

        log.info("[Reset] Contraseña actualizada para usuario id: {}", prt.getUser().getId());

        auditService.logBusiness(prt.getUser().getId(), "RESET_PASSWORD", "Auth", prt.getUser().getId(), true, null);
    }

    // ─── Utilidades ───────────────────────────────────────────────────

    private String generateRawToken() {
        byte[] bytes = new byte[32];
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
            throw new IllegalStateException("No se pudo hashear el token", e);
        }
    }
}