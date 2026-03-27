package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.documents.model.DocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

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
        List<String> tagNames,
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

        // ✅ CORREGIDO: Document.documentTags → DocumentTag → Tag
        List<String> tags = d.getDocumentTags() != null
                ? d.getDocumentTags().stream()
                .filter(dt -> dt.getTag() != null)
                .map(dt -> dt.getTag().getName())
                .collect(Collectors.toList())
                : List.of();

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
                tags,
                d.getCreatedAt(),
                d.getUpdatedAt(),
                isFavorite
        );
    }
}