package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.dto.request.ShareRequest;
import com.docucloud.backend.documents.dto.response.ShareAccessResponse;
import com.docucloud.backend.documents.dto.response.ShareResponse;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentShare;
import com.docucloud.backend.documents.model.Permission;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.DocumentShareRepository;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.storage.s3.service.S3PresignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class ShareService {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository shareRepository;
    private final S3PresignService presignService;
    private final PasswordEncoder passwordEncoder;
    private final String baseUrl;
    private final long getMinutes;

    // ← Constructor explícito obligatorio por los @Value
    public ShareService(
            DocumentRepository documentRepository,
            DocumentShareRepository shareRepository,
            S3PresignService presignService,
            PasswordEncoder passwordEncoder,
            @Value("${app.base-url}") String baseUrl,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes
    ) {
        this.documentRepository = documentRepository;
        this.shareRepository = shareRepository;
        this.presignService = presignService;
        this.passwordEncoder = passwordEncoder;
        this.baseUrl = baseUrl;
        this.getMinutes = getMinutes;
    }

    // ─── 1. Crear enlace ──────────────────────────────────────────────────────
    public ShareResponse shareDocument(Long docId, ShareRequest request, Long userId) {

        documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "No tienes permisos sobre este documento"));

        DocumentShare share = DocumentShare.builder()
                .documentId(docId)
                .sharedByUserId(userId)
                .permission(request.getPermission())
                .passwordHash(
                        request.getPassword() != null
                                ? passwordEncoder.encode(request.getPassword())
                                : null
                )
                .expiresAt(
                        request.getExpiresDays() != null
                                ? Instant.now().plus(Duration.ofDays(request.getExpiresDays()))
                                : null
                )
                .revoked(false)
                .usedCount(0)   // ← inicializar explícito
                .build();

        share = shareRepository.save(share);

        String shareUrl = baseUrl + "/api/documents/shares/" + share.getId() + "/access";

        log.info("🔗 Share created - user={} doc={} shareId={} permission={}",
                userId, docId, share.getId(), request.getPermission());

        return new ShareResponse(shareUrl, share.getId(), share.getExpiresAt());
    }

    // ─── 2. Revocar enlace ────────────────────────────────────────────────────
    public void revokeShare(UUID shareId, Long userId) {

        DocumentShare share = shareRepository.findByIdAndRevokedFalse(shareId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Share no encontrado o ya revocado"));

        if (!share.getSharedByUserId().equals(userId))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tienes permisos para revocar este enlace");

        share.setRevoked(true);
        shareRepository.save(share);

        log.info("🚫 Share revoked - user={} shareId={}", userId, shareId);
    }

    // ─── 3. Acceder al enlace (sin auth obligatorio) ──────────────────────────
    public ShareAccessResponse accessShare(UUID shareId, String password) {  // ← ShareAccessResponse, no PresignedUrlResponse

        DocumentShare share = shareRepository.findByIdAndRevokedFalse(shareId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Enlace inválido o revocado"));

        // Verificar expiración
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.GONE, "Este enlace ha expirado");

        // Verificar contraseña
        if (share.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, share.getPasswordHash()))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contraseña incorrecta");
        }

        // Obtener documento sin filtro de owner
        Document doc = documentRepository
                .findByIdAndDeletedAtIsNull(share.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no disponible"));

        // Incrementar contador
        share.setUsedCount(share.getUsedCount() + 1);
        shareRepository.save(share);

        // Generar presigned URL
        PresignedUrlResponse s3Url = presignService.presignGet(
                doc.getS3Bucket(),
                doc.getS3Key(),
                Duration.ofMinutes(getMinutes)
        );

        log.info("📥 Share accessed - shareId={} usedCount={}", shareId, share.getUsedCount());

        // ← s3Url tiene 3 campos: url, expiresAt, method
        return new ShareAccessResponse(
                s3Url.url(),
                s3Url.expiresAt(),
                share.getPermission() == Permission.WRITE
        );
    }
}
