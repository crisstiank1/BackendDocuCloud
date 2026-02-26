package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String fileHash,
        DocumentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document d) {
        if (d == null) return null;
        return new DocumentResponse(
                d.getId(),
                d.getFileName() != null ? d.getFileName() : "",
                d.getMimeType() != null ? d.getMimeType() : "",
                d.getSizeBytes() != null ? d.getSizeBytes() : 0L,
                d.getFileHash() != null ? d.getFileHash() : "",
                d.getStatus() != null ? d.getStatus() : DocumentStatus.PENDING_UPLOAD,
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
