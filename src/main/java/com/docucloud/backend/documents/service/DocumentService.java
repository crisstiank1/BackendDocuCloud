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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class DocumentService {

    private final DocumentRepository repo;
    private final S3KeyService keyService;
    private final S3PresignService presignService;

    private final String bucket;
    private final Duration putDuration;
    private final Duration getDuration;

    public DocumentService(
            DocumentRepository repo,
            S3KeyService keyService,
            S3PresignService presignService,
            @Value("${docucloud.aws.s3.bucket}") String bucket,
            @Value("${docucloud.aws.s3.presignPutMinutes:10}") long putMinutes,
            @Value("${docucloud.aws.s3.presignGetMinutes:10}") long getMinutes
    ) {
        this.repo = repo;
        this.keyService = keyService;
        this.presignService = presignService;
        this.bucket = bucket;
        this.putDuration = Duration.ofMinutes(putMinutes);
        this.getDuration = Duration.ofMinutes(getMinutes);
    }

    public InitUploadResponse initUpload(Long userId, InitUploadRequest req) {
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
        return new InitUploadResponse(doc.getId(), url.url(), url.expiresAt(), s3Key);
    }

    public void completeUpload(Long userId, Long docId, CompleteUploadRequest req) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setSizeBytes(req.sizeBytes());
        doc.setFileHash(req.fileHash());
        doc.setStatus(DocumentStatus.AVAILABLE);
        repo.save(doc);
    }

    public Page<Document> list(Long userId, Pageable pageable) {
        return repo.findAllByOwnerUserIdAndDeletedAtIsNull(userId, pageable);
    }

    public DownloadUrlResponse getDownloadUrl(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (doc.getStatus() != DocumentStatus.AVAILABLE) {
            throw new IllegalStateException("Document is not available");
        }

        PresignedUrlResponse url = presignService.presignGet(doc.getS3Bucket(), doc.getS3Key(), getDuration);
        return new DownloadUrlResponse(url.url(), url.expiresAt());
    }

    public void softDelete(Long userId, Long docId) {
        Document doc = repo.findByIdAndOwnerUserIdAndDeletedAtIsNull(docId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setDeletedAt(Instant.now());
        doc.setStatus(DocumentStatus.DELETED);
        repo.save(doc);
    }
}
