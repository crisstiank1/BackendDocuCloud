package com.docucloud.backend.documents.service;

import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.response.DownloadUrlResponse;
import com.docucloud.backend.documents.dto.response.InitUploadResponse;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.storage.s3.service.S3KeyService;
import com.docucloud.backend.storage.s3.service.S3PresignService;
import com.docucloud.backend.users.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

@Service
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository repo;
    private final S3KeyService keyService;
    private final S3PresignService presignService;
    private final UserRepository userRepository;

    private final String bucket;
    private final Duration putDuration;
    private final Duration getDuration;
    private final long maxSizeMb;

    public DocumentService(
            DocumentRepository repo,
            S3KeyService keyService,
            S3PresignService presignService,
            UserRepository userRepository,
            @Value("${docucloud.aws.s3.bucket}") String bucket,
            @Value("${docucloud.aws.s3.presignPutMinutes:10}") long putMinutes,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes,
            @Value("${app.document.max-size-mb:50}") long maxSizeMb
    ) {
        this.repo = repo;
        this.keyService = keyService;
        this.presignService = presignService;
        this.userRepository = userRepository;
        this.bucket = bucket;
        this.putDuration = Duration.ofMinutes(putMinutes);
        this.getDuration = Duration.ofMinutes(getMinutes);
        this.maxSizeMb = maxSizeMb;
    }

    public InitUploadResponse initUpload(Long userId, InitUploadRequest req) {
        // SOLO validar tamaño
        if (req.sizeBytes() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("Archivo excede 50MB");
        }

        String s3Key = keyService.buildDocumentKey(userId, req.fileName());

        Document doc = new Document();
        doc.setOwnerUserId(userId);
        doc.setFileName(req.fileName());
        doc.setMimeType(req.mimeType());
        doc.setSizeBytes(req.sizeBytes());
        doc.setS3Bucket(bucket);
        doc.setS3Key(s3Key);
        doc.setStatus(DocumentStatus.PENDING_UPLOAD);

        doc = repo.save(doc);

        PresignedUrlResponse url = presignService.presignPut(bucket, s3Key, req.mimeType(), putDuration);

        log.info("✅ Init upload user={} doc={} size={}MB", userId, doc.getId(), req.sizeBytes()/1024/1024);

        return new InitUploadResponse(doc.getId(), url.url(), url.expiresAt(), s3Key);
    }


    public void completeUpload(Long userId, Long docId, CompleteUploadRequest req) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setSizeBytes(req.sizeBytes());
        doc.setFileHash(req.fileHash());
        doc.setStatus(DocumentStatus.AVAILABLE);
        repo.save(doc);

        logActivity(userId, docId, "UPLOAD_COMPLETED");
    }

    public Page<Document> list(Long userId, Pageable pageable) {
        return repo.findAllByOwnerUserIdAndDeletedAtIsNull(userId, pageable);
    }

    public Page<Document> getRecentDocuments(Long userId, Pageable pageable) {
        log.info("🔍 Recent query userId={} pageable={}", userId, pageable);
        Page<Document> page = repo.findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, DocumentStatus.AVAILABLE, pageable);
        log.info("📊 Recent result: total={} content={}", page.getTotalElements(), page.getContent().size());
        return page;
    }



    public Page<Document> getActivityHistory(Long userId, Pageable pageable) {
        return getRecentDocuments(userId, pageable);
    }

    public DownloadUrlResponse getDownloadUrl(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (doc.getStatus() != DocumentStatus.AVAILABLE) {
            throw new IllegalStateException("Document is not available");
        }

        PresignedUrlResponse url = presignService.presignGet(doc.getS3Bucket(), doc.getS3Key(), getDuration);
        logActivity(userId, docId, "DOWNLOAD_REQUESTED");

        return new DownloadUrlResponse(url.url(), url.expiresAt());
    }

    public Document getDocumentForDelete(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        log.info("🗑️ Confirm delete - user={} doc={}", userId, docId);
        return doc;
    }


    public Page<Document> search(
            Long userId,
            String query,
            String mimeType,
            String statusParam,
            String fromDate,
            String toDate,
            Pageable pageable) {

        // Normalizar parámetros
        String nameQuery = (query == null || query.trim().isBlank()) ? null : "%" + query.trim().toLowerCase() + "%";
        String mimeQuery = (mimeType == null || mimeType.trim().isBlank()) ? null : mimeType.trim();

        DocumentStatus status = null;
        if (statusParam != null && !statusParam.trim().isBlank()) {
            try {
                status = DocumentStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("status inválido: " + statusParam + " (valores: " +
                        Arrays.toString(DocumentStatus.values()) + ")");
            }
        }

        Instant fromInstant = null;
        if (fromDate != null && !fromDate.trim().isBlank()) {
            try {
                // Mejor: soporta timezone local (Medellín -05)
                LocalDate localDate = LocalDate.parse(fromDate.trim());
                fromInstant = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception e) {
                throw new IllegalArgumentException("fromDate inválido: " + fromDate + " (formato: YYYY-MM-DD)");
            }
        }

        Instant toInstant = null;
        if (toDate != null && !toDate.trim().isBlank()) {
            try {
                LocalDate localDate = LocalDate.parse(toDate.trim());
                toInstant = localDate.atTime(23, 59, 59, 999_000_000).atOffset(ZoneOffset.UTC).toInstant();
            } catch (Exception e) {
                throw new IllegalArgumentException("toDate inválido: " + toDate + " (formato: YYYY-MM-DD)");
            }
        }

        // Si NO hay filtros = listar todos
        if (nameQuery == null && mimeQuery == null && status == null && fromInstant == null && toInstant == null) {
            return repo.findAllByOwnerUserIdAndDeletedAtIsNull(userId, pageable);
        }

        // ← CORREGIDO: ahora pasa 6 parámetros correctamente
        return repo.searchDocuments(userId, nameQuery, mimeQuery, status, fromInstant, toInstant, pageable);
    }




    public void softDelete(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setDeletedAt(Instant.now());
        doc.setStatus(DocumentStatus.DELETED);
        repo.save(doc);

        logActivity(userId, docId, "DELETED");
    }

    // Helpers privados
    private void validateFileSize(long sizeBytes) {
        if (sizeBytes > maxSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("Archivo excede límite de " + maxSizeMb + "MB");
        }
    }

    private void validateUserExists(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private void logActivity(Long userId, Long docId, String action) {
        log.info("📋 Activity - user={} doc={} action={}", userId, docId, action);
    }
}

