package com.docucloud.backend.documents.service;

import com.docucloud.backend.audit.annotation.Audited;
import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.response.DocumentResponse;
import com.docucloud.backend.documents.dto.response.DownloadUrlResponse;
import com.docucloud.backend.documents.dto.response.InitUploadResponse;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;
import com.docucloud.backend.documents.model.DocumentTag;
import com.docucloud.backend.documents.model.DocumentTagId;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.DocumentShareRepository;
import com.docucloud.backend.documents.repository.DocumentSpecification;
import com.docucloud.backend.documents.repository.DocumentTagRepository;
import com.docucloud.backend.favorites.service.FavoriteService;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.storage.s3.service.S3KeyService;
import com.docucloud.backend.storage.s3.service.S3PresignService;
import com.docucloud.backend.tags.dto.response.TagResponse;
import com.docucloud.backend.tags.model.Tag;
import com.docucloud.backend.tags.repository.TagRepository;
import com.docucloud.backend.users.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class DocumentService {

    @Autowired private TagRepository tagRepository;
    @Autowired private DocumentTagRepository documentTagRepository;
    @Autowired private ClassifierService classifierService;
    @Autowired private DocumentShareRepository shareRepository;

    private final DocumentRepository repo;
    private final S3KeyService keyService;
    private final S3PresignService presignService;
    private final UserRepository userRepository;
    private final FavoriteService favoriteService;

    private final String bucket;
    private final Duration putDuration;
    private final Duration getDuration;
    private final long maxSizeMb;

    public DocumentService(
            DocumentRepository repo,
            S3KeyService keyService,
            S3PresignService presignService,
            UserRepository userRepository,
            FavoriteService favoriteService,
            @Value("${docucloud.aws.s3.bucket}") String bucket,
            @Value("${docucloud.aws.s3.presignPutMinutes:10}") long putMinutes,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes,
            @Value("${app.document.max-size-mb:50}") long maxSizeMb
    ) {
        this.repo            = repo;
        this.keyService      = keyService;
        this.presignService  = presignService;
        this.userRepository  = userRepository;
        this.favoriteService = favoriteService;
        this.bucket          = bucket;
        this.putDuration     = Duration.ofMinutes(putMinutes);
        this.getDuration     = Duration.ofMinutes(getMinutes);
        this.maxSizeMb       = maxSizeMb;
    }

    // ─── UPLOAD ───────────────────────────────────────────────────────────────

    @Audited(action = "DOC_UPLOAD_INIT", resourceType = "Document")
    public InitUploadResponse initUpload(Long userId, InitUploadRequest req) {
        if (req.sizeBytes() > maxSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("Archivo excede " + maxSizeMb + "MB");
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

        PresignedUrlResponse url = presignService.presignPut(
                bucket, s3Key, req.mimeType(), putDuration);

        log.info("✅ Init upload user={} doc={} size={}MB",
                userId, doc.getId(), req.sizeBytes() / 1024 / 1024);
        return new InitUploadResponse(doc.getId(), url.url(), url.expiresAt(), s3Key);
    }

    @Audited(action = "DOC_UPLOAD_COMPLETE", resourceType = "Document", resourceIdArgIndex = 1)
    public void completeUpload(Long userId, Long docId, CompleteUploadRequest req) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setSizeBytes(req.sizeBytes());
        doc.setFileHash(req.fileHash());
        doc.setStatus(DocumentStatus.AVAILABLE);
        repo.save(doc);
        logActivity(userId, docId, "UPLOAD_COMPLETED");

        final Long   capturedDocId  = docId;
        final String capturedName   = doc.getFileName();
        final Long   capturedUserId = userId;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("🧠 Triggering async classification post-commit - docId={}",
                        capturedDocId);
                classifierService.classifyAndAssignAsync(
                        capturedDocId, capturedName, capturedUserId);
            }
        });
    }

    // ─── LISTADO ──────────────────────────────────────────────────────────────

    public Page<Document> list(Long userId, Pageable pageable) {
        return repo.findAllByOwnerUserIdAndDeletedAtIsNull(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listWithFavorites(Long userId, Pageable pageable) {
        Page<Document> page = repo.findAllByOwnerUserIdAndDeletedAtIsNull(userId, pageable);
        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listWithFavoritesByCategory(
            Long userId, Long categoryId, Pageable pageable) {
        Page<Document> page = repo.findByOwnerUserIdAndCategoryId(userId, categoryId, pageable);
        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    // ✅ NUEVO: documentos sin categoría asignada — filtra directo en BD
    @Transactional(readOnly = true)
    public Page<DocumentResponse> listUnclassified(Long userId, Pageable pageable) {
        Page<Document> page = repo.findUnclassifiedByOwnerUserId(userId, pageable);
        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    public Page<Document> getRecentDocuments(Long userId, Pageable pageable) {
        return repo.findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, DocumentStatus.AVAILABLE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getRecentDocumentsWithFavorites(Long userId, Pageable pageable) {
        Page<Document> page = repo.findByOwnerUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, DocumentStatus.AVAILABLE, pageable);
        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    public Page<Document> getActivityHistory(Long userId, Pageable pageable) {
        return getRecentDocuments(userId, pageable);
    }

    // ─── BÚSQUEDA ─────────────────────────────────────────────────────────────

    public Page<Document> search(
            Long userId, String query, String mimeType,
            String statusParam, String fromDate, String toDate, Pageable pageable) {

        String nameQuery = (query == null || query.isBlank()) ? null : query.trim();
        String mimeQuery = (mimeType == null || mimeType.isBlank()) ? null : mimeType.trim();

        DocumentStatus status = null;
        if (statusParam != null && !statusParam.isBlank()) {
            try {
                status = DocumentStatus.valueOf(statusParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "status inválido: '" + statusParam + "'. Valores permitidos: "
                                + Arrays.toString(DocumentStatus.values()));
            }
        }

        Instant fromInstant = null;
        if (fromDate != null && !fromDate.isBlank()) {
            try {
                fromInstant = LocalDate.parse(fromDate.trim())
                        .atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception e) {
                throw new IllegalArgumentException("fromDate inválido: '" + fromDate + "'");
            }
        }

        Instant toInstant = null;
        if (toDate != null && !toDate.isBlank()) {
            try {
                toInstant = LocalDate.parse(toDate.trim())
                        .atTime(23, 59, 59, 999_000_000)
                        .atOffset(ZoneOffset.UTC).toInstant();
            } catch (Exception e) {
                throw new IllegalArgumentException("toDate inválido: '" + toDate + "'");
            }
        }

        return repo.findAll(
                DocumentSpecification.search(
                        userId, nameQuery, mimeQuery, status, fromInstant, toInstant),
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> searchWithFavorites(
            Long userId, String query, String mimeType,
            String statusParam, String fromDate, String toDate, Pageable pageable) {
        Page<Document> page = search(
                userId, query, mimeType, statusParam, fromDate, toDate, pageable);
        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    // ─── DOWNLOAD ─────────────────────────────────────────────────────────────

    @Audited(action = "DOC_DOWNLOAD_URL", resourceType = "Document", resourceIdArgIndex = 1)
    public DownloadUrlResponse getDownloadUrl(Long userId, Long docId) {
        Document doc = findDocumentForUser(userId, docId);

        if (doc.getStatus() != DocumentStatus.AVAILABLE) {
            throw new IllegalStateException("El documento no está disponible");
        }

        PresignedUrlResponse url = presignService.presignGet(
                doc.getS3Bucket(), doc.getS3Key(), getDuration);
        logActivity(userId, docId, "DOWNLOAD_REQUESTED");
        return new DownloadUrlResponse(url.url(), url.expiresAt());
    }

    // ─── PREVIEW ──────────────────────────────────────────────────────────────

    public DownloadUrlResponse getPreviewUrl(Long userId, Long docId) {
        Document doc = findDocumentForUser(userId, docId);
        PresignedUrlResponse url = presignService.presignGet(
                doc.getS3Bucket(), doc.getS3Key(), getDuration);
        return new DownloadUrlResponse(url.url(), url.expiresAt());
    }

    public Document getDocumentByIdPublic(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));
    }

    public byte[] downloadFileBytes(Document document) {
        PresignedUrlResponse presigned = presignService.presignGet(
                bucket,
                document.getS3Key(),
                Duration.ofMinutes(10)
        );
        try (InputStream is = new java.net.URI(presigned.url())
                .toURL().openStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Error descargando archivo de S3", e);
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Audited(action = "DOC_DELETE", resourceType = "Document", resourceIdArgIndex = 1)
    public void softDelete(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setDeletedAt(Instant.now());
        doc.setStatus(DocumentStatus.DELETED);
        repo.save(doc);
        logActivity(userId, docId, "DELETED");
    }

    // ─── TAGS ─────────────────────────────────────────────────────────────────

    @Transactional
    public void addTagToDocument(Long documentId, Long tagId, Long userId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found or access denied"));

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found"));

        DocumentTagId id = new DocumentTagId(documentId, tagId);
        if (documentTagRepository.existsById(id)) {
            log.info("🏷️ Tag {} already associated with document {}", tagId, documentId);
            return;
        }

        long currentTags = documentTagRepository.findByDocumentId(documentId).size();
        if (currentTags >= 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Máximo 3 etiquetas por documento");
        }

        DocumentTag docTag = DocumentTag.builder().document(doc).tag(tag).build();
        documentTagRepository.save(docTag);
        log.info("✅ Tag '{}' added to document '{}'", tag.getName(), doc.getFileName());
    }

    @Transactional
    public void removeTagFromDocument(Long documentId, Long tagId, Long userId) {
        repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        documentTagRepository.deleteByDocumentIdAndTagId(documentId, tagId);
    }

    public List<TagResponse> getDocumentTags(Long documentId, Long userId) {
        return documentTagRepository.findByDocumentId(documentId)
                .stream()
                .map(dt -> new TagResponse(dt.getTag().getId(), dt.getTag().getName()))
                .collect(Collectors.toList());
    }

    // ─── STORAGE ──────────────────────────────────────────────────────────────

    public long getStorageUsedByUser(Long userId) {
        return repo
                .findByOwnerUserIdAndStatusNotAndDeletedAtIsNull(userId, DocumentStatus.DELETED)
                .stream()
                .mapToLong(Document::getSizeBytes)
                .sum();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private void logActivity(Long userId, Long docId, String action) {
        log.info("📋 Activity - user={} doc={} action={}", userId, docId, action);
    }

    private Set<Long> getFavoriteIds(Long userId, Page<Document> page) {
        Set<Long> docIds = page.getContent().stream()
                .map(Document::getId)
                .collect(Collectors.toSet());
        return favoriteService.getFavoriteIdsByDocumentIds(userId, docIds);
    }

    private Document findDocumentForUser(Long userId, Long docId) {
        Optional<Document> owned = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId);
        if (owned.isPresent()) return owned.get();

        String userEmail = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"))
                .getEmail();

        boolean hasShare = shareRepository
                .findByDocumentIdAndRevokedFalse(docId)
                .stream()
                .anyMatch(s -> userEmail.equals(s.getRecipientEmail()));

        if (hasShare) {
            return repo.findByIdAndDeletedAtIsNull(docId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        }

        throw new IllegalArgumentException("Document not found or access denied");
    }
}