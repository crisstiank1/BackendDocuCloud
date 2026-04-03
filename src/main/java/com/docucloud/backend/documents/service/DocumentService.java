package com.docucloud.backend.documents.service;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.documents.dto.request.CompleteUploadRequest;
import com.docucloud.backend.documents.dto.request.InitUploadRequest;
import com.docucloud.backend.documents.dto.request.UpdateMetadataRequest;
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
import com.docucloud.backend.documents.validation.AllowedFileTypes;
import com.docucloud.backend.favorites.service.FavoriteService;
import com.docucloud.backend.storage.s3.dto.PresignedUrlResponse;
import com.docucloud.backend.storage.s3.service.S3KeyService;
import com.docucloud.backend.storage.s3.service.S3PresignService;
import com.docucloud.backend.tags.dto.response.TagResponse;
import com.docucloud.backend.tags.model.Tag;
import com.docucloud.backend.tags.repository.TagRepository;
import com.docucloud.backend.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@Transactional
public class DocumentService {

    private final DocumentRepository repo;
    private final DocumentTagRepository documentTagRepository;
    private final DocumentShareRepository shareRepository;
    private final TagRepository tagRepository;
    private final ClassifierService classifierService;
    private final S3KeyService keyService;
    private final S3PresignService presignService;
    private final UserRepository userRepository;
    private final FavoriteService favoriteService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final Duration putDuration;
    private final Duration getDuration;
    private final long maxSizeMb;

    public DocumentService(
            DocumentRepository repo,
            DocumentTagRepository documentTagRepository,
            DocumentShareRepository shareRepository,
            TagRepository tagRepository,
            ClassifierService classifierService,
            S3KeyService keyService,
            S3PresignService presignService,
            UserRepository userRepository,
            FavoriteService favoriteService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${docucloud.aws.s3.bucket}") String bucket,
            @Value("${docucloud.aws.s3.presignPutMinutes:10}") long putMinutes,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes,
            @Value("${app.document.max-size-mb:50}") long maxSizeMb
    ) {
        this.repo = repo;
        this.documentTagRepository = documentTagRepository;
        this.shareRepository = shareRepository;
        this.tagRepository = tagRepository;
        this.classifierService = classifierService;
        this.keyService = keyService;
        this.presignService = presignService;
        this.userRepository = userRepository;
        this.favoriteService = favoriteService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.putDuration = Duration.ofMinutes(putMinutes);
        this.getDuration = Duration.ofMinutes(getMinutes);
        this.maxSizeMb = maxSizeMb;
    }

    // ─── UPLOAD ───────────────────────────────────────────────────────────────

    public InitUploadResponse initUpload(Long userId, InitUploadRequest req) {
        if (!AllowedFileTypes.isAllowed(req.fileName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tipo de archivo no permitido: " + getFileExtension(req.fileName()));
        }

        if (req.sizeBytes() > maxSizeMb * 1024 * 1024) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Archivo excede " + maxSizeMb + "MB");
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

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "sin extensión";
        }
        return fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    }

    public void completeUpload(Long userId, Long docId, CompleteUploadRequest req) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        boolean success = true;
        try {
            doc.setSizeBytes(req.sizeBytes());
            doc.setFileHash(req.fileHash());
            doc.setStatus(DocumentStatus.AVAILABLE);
            repo.save(doc);

            final Long capturedDocId = docId;
            final String capturedName = doc.getFileName();
            final Long capturedUserId = userId;

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("🧠 Triggering async classification post-commit - docId={}", capturedDocId);
                    classifierService.classifyAndAssignAsync(capturedDocId, capturedName, capturedUserId);
                }
            });
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", doc.getFileName());
            auditService.logBusiness(userId, "UPLOAD_DOCUMENT", "Document", docId, success, details);
        }
    }

    // ─── LISTADO ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listUnclassified(Long userId, Pageable pageable) {
        Page<Document> page = repo.findUnclassifiedByOwnerUserId(userId, pageable);
        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Page<Document> getActivityHistory(Long userId, Pageable pageable) {
        return getRecentDocuments(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listFailedWithFavorites(Long userId, Pageable pageable) {
        Page<Document> page = repo.findByOwnerUserIdAndStatusAndDeletedAtIsNull(
                userId,
                DocumentStatus.FAILED,
                pageable
        );

        Set<Long> favIds = getFavoriteIds(userId, page);
        return page.map(doc -> DocumentResponse.from(doc, favIds.contains(doc.getId())));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentResponseById(Long userId, Long documentId) {
        Document doc = findDocumentForUser(userId, documentId);

        boolean isFavorite = favoriteService
                .getFavoriteIdsByDocumentIds(userId, Set.of(documentId))
                .contains(documentId);

        return DocumentResponse.from(doc, isFavorite);
    }

    // ─── BÚSQUEDA ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
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
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
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
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "fromDate inválido: '" + fromDate + "'");
            }
        }

        Instant toInstant = null;
        if (toDate != null && !toDate.isBlank()) {
            try {
                toInstant = LocalDate.parse(toDate.trim())
                        .atTime(23, 59, 59, 999_000_000)
                        .atOffset(ZoneOffset.UTC).toInstant();
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "toDate inválido: '" + toDate + "'");
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

    public DownloadUrlResponse getDownloadUrl(Long userId, Long docId) {
        Document doc = findDocumentForUser(userId, docId);

        if (doc.getStatus() != DocumentStatus.AVAILABLE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El documento no está disponible");
        }

        boolean success = true;
        try {
            PresignedUrlResponse url = presignService.presignGet(
                    doc.getS3Bucket(), doc.getS3Key(), getDuration);
            return new DownloadUrlResponse(url.url(), url.expiresAt());
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", doc.getFileName());
            auditService.logBusiness(userId, "DOWNLOAD_DOCUMENT", "Document", docId, success, details);
        }
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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));
    }

    public byte[] downloadFileBytes(Document document) {
        PresignedUrlResponse presigned = presignService.presignGet(
                bucket, document.getS3Key(), Duration.ofMinutes(10));
        try (InputStream is = new java.net.URI(presigned.url()).toURL().openStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error descargando archivo de S3");
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    public void softDelete(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        boolean success = true;
        try {
            doc.setDeletedAt(Instant.now());
            doc.setStatus(DocumentStatus.DELETED);
            repo.save(doc);
            log.info("🗑️ Document deleted - user={} doc={}", userId, docId);
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", doc.getFileName());
            auditService.logBusiness(userId, "DELETE_DOCUMENT", "Document", docId, success, details);
        }
    }

    // ─── TAGS ─────────────────────────────────────────────────────────────────

    @Transactional
    public void addTagToDocument(Long documentId, Long tagId, Long userId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Tag no encontrado"));

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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));
        documentTagRepository.deleteByDocumentIdAndTagId(documentId, tagId);
    }

    @Transactional(readOnly = true)
    public List<TagResponse> getDocumentTags(Long documentId, Long userId) {
        return documentTagRepository.findByDocumentId(documentId)
                .stream()
                .map(dt -> new TagResponse(dt.getTag().getId(), dt.getTag().getName()))
                .collect(Collectors.toList());
    }

    // ─── STORAGE ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long getStorageUsedByUser(Long userId) {
        return repo.sumStorageByUser(userId);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"))
                .getEmail();

        boolean hasShare = shareRepository
                .findByDocumentIdAndRevokedFalse(docId)
                .stream()
                .anyMatch(s -> userEmail.equals(s.getRecipientEmail()));

        if (hasShare) {
            return repo.findByIdAndDeletedAtIsNull(docId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Documento no encontrado"));
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Documento no encontrado o acceso denegado");
    }

    // ─── UPDATE METADATA ──────────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse updateMetadata(Long userId, Long docId, UpdateMetadataRequest req) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Documento no encontrado"));

        boolean success = true;
        try {
            // Renombrar si viene el campo
            if (req.fileName() != null && !req.fileName().isBlank()) {
                String newName = req.fileName().trim();

                // Validar extensión: no permitir cambiar el tipo de archivo
                String oldExt = getFileExtension(doc.getFileName()).toLowerCase();
                String newExt = getFileExtension(newName).toLowerCase();
                if (!oldExt.equals(newExt)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "No se puede cambiar la extensión del archivo (." +
                                    oldExt + " → ." + newExt + ")");
                }

                doc.setFileName(newName);
            }

            doc = repo.save(doc);
            log.info("✏️ Metadata updated - user={} doc={} newName={}", userId, docId, doc.getFileName());

        } catch (ResponseStatusException ex) {
            success = false;
            throw ex;
        } catch (Exception ex) {
            success = false;
            log.error("❌ Error updating metadata for doc={}", docId, ex);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Error al actualizar el documento");
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", doc.getFileName());
            auditService.logBusiness(userId, "UPDATE_METADATA", "Document", docId, success, details);
        }

        boolean isFavorite = favoriteService
                .getFavoriteIdsByDocumentIds(userId, Set.of(docId))
                .contains(docId);

        return DocumentResponse.from(doc, isFavorite);
    }
}