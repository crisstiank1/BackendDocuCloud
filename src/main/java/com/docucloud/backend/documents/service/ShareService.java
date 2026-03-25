package com.docucloud.backend.documents.service;

import com.docucloud.backend.common.service.EmailService;
import com.docucloud.backend.documents.dto.request.ShareRequest;
import com.docucloud.backend.documents.dto.response.*;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentShare;
import com.docucloud.backend.documents.model.Permission;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.DocumentShareRepository;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.storage.s3.service.S3PresignService;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ShareService {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository shareRepository;
    private final S3PresignService presignService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final String baseUrl;
    private final long getMinutes;

    public ShareService(
            DocumentRepository documentRepository,
            DocumentShareRepository shareRepository,
            S3PresignService presignService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            UserRepository userRepository,
            @Value("${app.base-url}") String baseUrl,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes
    ) {
        this.documentRepository = documentRepository;
        this.shareRepository = shareRepository;
        this.presignService = presignService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
        this.getMinutes = getMinutes;
    }

    // ─── 1. Crear share ───────────────────────────────────────────────────────

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
                .passwordHash(request.getPassword() != null
                        ? passwordEncoder.encode(request.getPassword()) : null)
                .expiresAt(request.getExpiresDays() != null
                        ? Instant.now().plus(Duration.ofDays(request.getExpiresDays())) : null)
                .revoked(false)
                .usedCount(0)
                .recipientEmail(request.getRecipientEmail())
                .build();

        share = shareRepository.save(share);

        if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            emailService.sendShareGranted(
                    request.getRecipientEmail(),
                    doc.getFileName(),
                    share.getPermission().name()
            );
        }

        String shareUrl = baseUrl + "/api/documents/shares/" + share.getId() + "/access";
        log.info("🔗 Share created - user={} doc={} shareId={} permission={} recipient={}",
                userId, docId, share.getId(), request.getPermission(), request.getRecipientEmail());

        return new ShareResponse(shareUrl, share.getId(), share.getExpiresAt());
    }

    // ─── 2. Shares activos de un documento (para el modal de compartir) ───────

    @Transactional(readOnly = true)
    public List<ShareSummaryResponse> getDocumentShares(Long docId, Long userId) {
        documentRepository
                .findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "No tienes permisos sobre este documento"));

        return shareRepository.findByDocumentIdAndRevokedFalse(docId)
                .stream()
                .map(share -> new ShareSummaryResponse(
                        share.getId(),
                        share.getDocumentId(),
                        null,
                        share.getPermission(),
                        share.getPasswordHash() != null,
                        share.isRevoked(),
                        share.getUsedCount(),
                        share.getRecipientEmail(),
                        share.getExpiresAt(),
                        share.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    // ─── 3. Revocar share ─────────────────────────────────────────────────────

    public void revokeShare(UUID shareId, Long userId) {
        DocumentShare share = shareRepository.findByIdAndRevokedFalse(shareId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Share no encontrado o ya revocado"));

        if (!share.getSharedByUserId().equals(userId))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tienes permisos para revocar este enlace");

        if (share.getRecipientEmail() != null) {
            Document doc = documentRepository
                    .findByIdAndDeletedAtIsNull(share.getDocumentId())
                    .orElse(null);
            if (doc != null)
                emailService.sendShareRevoked(share.getRecipientEmail(), doc.getFileName());
        }

        share.setRevoked(true);
        shareRepository.save(share);
        log.info("🚫 Share revoked - user={} shareId={}", userId, shareId);
    }

    // ─── 4. Acceder al enlace ─────────────────────────────────────────────────

    public ShareAccessResponse accessShare(UUID shareId, String password) {
        DocumentShare share = validateShare(shareId, password);

        Document doc = documentRepository
                .findByIdAndDeletedAtIsNull(share.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no disponible"));

        share.setUsedCount(share.getUsedCount() + 1);
        shareRepository.save(share);

        PresignedUrlResponse s3Url = presignService.presignGet(
                doc.getS3Bucket(), doc.getS3Key(), Duration.ofMinutes(getMinutes));

        log.info("📥 Share accessed - shareId={} permission={} usedCount={}",
                shareId, share.getPermission(), share.getUsedCount());

        return new ShareAccessResponse(s3Url.url(), s3Url.expiresAt(),
                share.getPermission() == Permission.WRITE);
    }

    // ─── 5. URL de escritura ──────────────────────────────────────────────────

    public PresignedUrlResponse getWriteUrl(UUID shareId, String password,
                                            String mimeType, Long userId) {
        DocumentShare share = validateShare(shareId, password);

        if (share.getPermission() != Permission.WRITE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este enlace es solo lectura");

        Document doc = documentRepository
                .findByIdAndDeletedAtIsNull(share.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no disponible"));

        log.info("✏️ Write URL generated via share - shareId={} doc={}", shareId, doc.getId());

        return presignService.presignPut(
                doc.getS3Bucket(), doc.getS3Key(), mimeType, Duration.ofMinutes(getMinutes));
    }

    // ─── 6. Mis shares enviados ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ShareSummaryResponse> getMyShares(Long userId, boolean includeRevoked, Pageable pageable) {
        Page<DocumentShare> shares = includeRevoked
                ? shareRepository.findBySharedByUserIdOrderByCreatedAtDesc(userId, pageable)
                : shareRepository.findBySharedByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId, pageable);

        Set<Long> docIds = shares.stream()
                .map(DocumentShare::getDocumentId)
                .collect(Collectors.toSet());

        Map<Long, String> fileNames = documentRepository.findAllById(docIds)
                .stream()
                .collect(Collectors.toMap(Document::getId, Document::getFileName));

        return shares.map(share -> new ShareSummaryResponse(
                share.getId(),
                share.getDocumentId(),
                fileNames.getOrDefault(share.getDocumentId(), "Documento eliminado"),
                share.getPermission(),
                share.getPasswordHash() != null,
                share.isRevoked(),
                share.getUsedCount(),
                share.getRecipientEmail(),
                share.getExpiresAt(),
                share.getCreatedAt()
        ));
    }

    // ─── 7. Compartidos por mí (agrupado por documento) ──────────────────────

    @Transactional(readOnly = true)
    public Page<SharedByMeResponse> getSharedByMe(Long userId, Pageable pageable) {
        Page<Document> docs = documentRepository.findSharedByMe(userId, pageable);

        return docs.map(doc -> {

            List<SharedByMeResponse.ShareSummary> summaries = shareRepository
                    .findByDocumentIdAndSharedByUserIdAndRevokedFalse(doc.getId(), userId)
                    .stream()
                    .map(s -> SharedByMeResponse.ShareSummary.builder()
                            .shareId(s.getId().toString())
                            .recipientEmail(s.getRecipientEmail())
                            .permission(s.getPermission().name())
                            .hasPassword(s.getPasswordHash() != null)
                            .usedCount(s.getUsedCount())
                            .expiresAt(s.getExpiresAt() != null ? s.getExpiresAt().toString() : null)
                            .createdAt(s.getCreatedAt().toString())
                            .build())
                    .collect(Collectors.toList());

            // Generamos la URL presignada solo si es imagen
            String thumbnailUrl = generateThumbnailUrl(doc);

            return SharedByMeResponse.builder()
                    .documentId(doc.getId())
                    .fileName(doc.getFileName())
                    .mimeType(doc.getMimeType())
                    .sizeBytes(doc.getSizeBytes())
                    .createdAt(doc.getCreatedAt().toString())
                    .thumbnailUrl(thumbnailUrl)
                    .shares(summaries)
                    .build();
        });
    }


    // ─── 8. Compartidos conmigo  ──────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SharedWithMeResponse> getSharedWithMe(String email, Pageable pageable) {
        return shareRepository
                .findActiveSharesWithAvailableDocuments(email, pageable)
                .map(share -> {
                    Document doc = documentRepository
                            .findByIdAndDeletedAtIsNull(share.getDocumentId())
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Documento no disponible"));

                    User sharedBy = userRepository.findById(share.getSharedByUserId())
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Usuario no encontrado"));

                    // Generamos la URL presignada solo si es imagen
                    String thumbnailUrl = generateThumbnailUrl(doc);

                    return SharedWithMeResponse.from(share, doc, sharedBy, generateThumbnailUrl(doc));
                });
    }

    // ─── 9. Actualizar permiso ────────────────────────────────────────────────

    public ShareResponse updateSharePermission(UUID shareId, Permission newPermission, Long userId) {
        DocumentShare share = shareRepository.findByIdAndRevokedFalse(shareId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Share no encontrado o ya revocado"));

        if (!share.getSharedByUserId().equals(userId))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tienes permisos para modificar este enlace");

        share.setPermission(newPermission);
        shareRepository.save(share);

        if (share.getRecipientEmail() != null) {
            Document doc = documentRepository
                    .findByIdAndDeletedAtIsNull(share.getDocumentId())
                    .orElse(null);
            if (doc != null)
                emailService.sendPermissionChanged(
                        share.getRecipientEmail(),
                        doc.getFileName(),
                        newPermission.name()
                );
        }

        log.info("🔄 Share permission updated - user={} shareId={} newPermission={}",
                userId, shareId, newPermission);

        String shareUrl = baseUrl + "/api/documents/shares/" + share.getId() + "/access";
        return new ShareResponse(shareUrl, share.getId(), share.getExpiresAt());
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
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contraseña incorrecta");
        }

        return share;
    }

    // ─── Destinatario elimina su propio acceso ────────────────────────────────

    public void removeSharedWithMe(UUID shareId, String email) {
        DocumentShare share = shareRepository
                .findByIdAndRecipientEmailAndRevokedFalse(shareId, email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Share no encontrado"));
        share.setRevoked(true);
        shareRepository.save(share);
        log.info("🗑️ Share removido por destinatario - shareId={} email={}", shareId, email);
    }

    // ─── URL de escritura para destinatario con permiso WRITE ─────────────────

    public PresignedUrlResponse getWriteUrlForRecipient(UUID shareId, String email, String mimeType) {
        DocumentShare share = shareRepository
                .findByIdAndRecipientEmailAndRevokedFalse(shareId, email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Share no encontrado"));

        if (share.getPermission() != Permission.WRITE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso de escritura");

        Document doc = documentRepository
                .findByIdAndDeletedAtIsNull(share.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no disponible"));

        return presignService.presignPut(
                doc.getS3Bucket(), doc.getS3Key(), mimeType, Duration.ofMinutes(getMinutes));
    }

    // ─── Helper: URL presignada para miniaturas de imágenes ───────────────────────

    private String generateThumbnailUrl(Document doc) {
        if (doc.getMimeType() == null || !doc.getMimeType().startsWith("image/")) {
            return null;
        }
        try {
            return presignService
                    .presignGet(doc.getS3Bucket(), doc.getS3Key(), Duration.ofMinutes(getMinutes))
                    .url();
        } catch (Exception ex) {
            log.warn("⚠️ No se pudo generar thumbnailUrl para doc={}: {}", doc.getId(), ex.getMessage());
            return null;
        }
    }
}