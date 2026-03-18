package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record DocumentResponse(
        Long id,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String fileHash,
        DocumentStatus status,
        Long folderId,
        Long categoryId,
        boolean isAutomaticallyAssigned,
        BigDecimal confidenceScore,
        Instant createdAt,
        Instant updatedAt,
        boolean isFavorite
) {

    public static DocumentResponse from(Document d) {
        return from(d, false);
    }

    public static DocumentResponse from(Document d, boolean isFavorite) {
        if (d == null) return null;

        Long catId = null;
        boolean auto = false;
        BigDecimal confidence = null;

        if (d.getClassification() != null) {
            catId      = d.getClassification().getCategory().getId();
            auto       = Boolean.TRUE.equals(d.getClassification().getIsAutomaticallyAssigned());
            confidence = d.getClassification().getConfidenceScore();
        }

        return new DocumentResponse(
                d.getId(),
                d.getFileName(),
                d.getMimeType(),
                d.getSizeBytes(),
                d.getFileHash(),
                d.getStatus(),
                d.getFolderId(),
                catId,
                auto,
                confidence,
                d.getCreatedAt(),
                d.getUpdatedAt(),
                isFavorite
        );
    }
}