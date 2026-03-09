package com.docucloud.backend.documents.service;

import com.docucloud.backend.common.service.EmailService;
import com.docucloud.backend.documents.dto.request.ShareRequest;
import com.docucloud.backend.documents.dto.response.ShareAccessResponse;
import com.docucloud.backend.documents.dto.response.ShareResponse;
import com.docucloud.backend.documents.dto.response.ShareSummaryResponse;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentShare;
import com.docucloud.backend.documents.model.Permission;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.DocumentShareRepository;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.storage.s3.service.S3PresignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final EmailService emailService;
    private final String baseUrl;
    private final long getMinutes;

    public ShareService(
            DocumentRepository documentRepository,
            DocumentShareRepository shareRepository,
            S3PresignService presignService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.base-url}") String baseUrl,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes
    ) {
        this.documentRepository = documentRepository;
        this.shareRepository = shareRepository;
        this.presignService = presignService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.baseUrl = baseUrl;
        this.getMinutes = getMinutes;
    }

    // ─── 1. Crear enlace ──────────────────────────────────────────────────────
    public ShareResponse shareDocument(Long docId, ShareRequest request, Long userId) {

        Document doc = documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "No tienes permisos sobre este documento"));

        if (request.getPermission() == null)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Debes especificar un permiso: READ o WRITE");

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
                .usedCount(0)
                .recipientEmail(request.getRecipientEmail())  // ✅ fix 1
                .build();

        share = shareRepository.save(share);

        // RF-33: notificar al receptor si se especificó email
        if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            emailService.sendShareGranted(
                    request.getRecipientEmail(),
                    doc.getFileName(),                        // ✅ fix 2
                    share.getPermission().name()
            );
        }

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

        // RF-33: notificar al receptor antes de revocar
        if (share.getRecipientEmail() != null) {
            Document doc = documentRepository
                    .findByIdAndDeletedAtIsNull(share.getDocumentId())
                    .orElse(null);
            if (doc != null)
                emailService.sendShareRevoked(share.getRecipientEmail(), doc.getFileName()); // ✅ fix 3
        }

        share.setRevoked(true);
        shareRepository.save(share);

        log.info("🚫 Share revoked - user={} shareId={}", userId, shareId);
    }

    // ─── 3. Acceder al enlace (lectura) ───────────────────────────────────────
    public ShareAccessResponse accessShare(UUID shareId, String password) {

        DocumentShare share = validateShare(shareId, password);

        Document doc = documentRepository
                .findByIdAndDeletedAtIsNull(share.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no disponible"));

        share.setUsedCount(share.getUsedCount() + 1);
        shareRepository.save(share);

        PresignedUrlResponse s3Url = presignService.presignGet(
                doc.getS3Bucket(),
                doc.getS3Key(),
                Duration.ofMinutes(getMinutes)
        );

        log.info("📥 Share accessed - shareId={} permission={} usedCount={}",
                shareId, share.getPermission(), share.getUsedCount());

        return new ShareAccessResponse(
                s3Url.url(),
                s3Url.expiresAt(),
                share.getPermission() == Permission.WRITE
        );
    }

    // ─── 4. RF-32: URL de escritura (solo WRITE) ──────────────────────────────
    public PresignedUrlResponse getWriteUrl(UUID shareId, String password,
                                            String mimeType, Long userId) {

        DocumentShare share = validateShare(shareId, password);

        if (share.getPermission() != Permission.WRITE)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Este enlace es solo lectura");

        Document doc = documentRepository
                .findByIdAndDeletedAtIsNull(share.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no disponible"));

        log.info("✏️ Write URL generated via share - shareId={} doc={}",
                shareId, doc.getId());

        return presignService.presignPut(
                doc.getS3Bucket(),
                doc.getS3Key(),
                mimeType,
                Duration.ofMinutes(getMinutes)
        );
    }

    public Page<ShareSummaryResponse> getMyShares(Long userId, boolean includeRevoked, Pageable pageable) {
        Page<DocumentShare> shares = includeRevoked
                ? shareRepository.findBySharedByUserIdOrderByCreatedAtDesc(userId, pageable)
                : shareRepository.findBySharedByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId, pageable);
        return shares.map(ShareSummaryResponse::from);
    }

    // ─── Helper: validar share ────────────────────────────────────────────────
    private DocumentShare validateShare(UUID shareId, String password) {

        DocumentShare share = shareRepository.findByIdAndRevokedFalse(shareId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Enlace inválido o revocado"));

        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.GONE, "Este enlace ha expirado");

        if (share.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, share.getPasswordHash()))
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Contraseña incorrecta");
        }

        return share;
    }
}
