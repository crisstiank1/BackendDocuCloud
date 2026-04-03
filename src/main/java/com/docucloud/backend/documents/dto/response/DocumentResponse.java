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
        boolean isFavorite,
        String thumbnailUrl  // ✅ FIX: campo agregado para miniaturas de imágenes
) {

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Sin favorito ni thumbnail — para contextos internos */
    public static DocumentResponse from(Document d) {
        return from(d, false, null);
    }

    /** Con favorito, sin thumbnail — compatibilidad con llamadas existentes */
    public static DocumentResponse from(Document d, boolean isFavorite) {
        return from(d, isFavorite, null);
    }

    /**
     * ✅ FIX: Método principal con thumbnailUrl.
     * Úsalo desde DocumentService pasando la URL presignada de S3
     * cuando el mimeType empiece por "image/".
     *
     * Ejemplo de uso en DocumentService:
     *   DocumentResponse.from(doc, isFavorite, resolveThumbnailUrl(doc))
     */
    public static DocumentResponse from(Document d, boolean isFavorite, String thumbnailUrl) {
        if (d == null) return null;

        Long catId = null;
        boolean auto = false;
        BigDecimal confidence = null;

        // null-check en getCategory() para evitar NullPointerException
        // si un documento tiene clasificación pero sin categoría asignada aún
        if (d.getClassification() != null) {
            if (d.getClassification().getCategory() != null) {
                catId = d.getClassification().getCategory().getId();
            }
            auto       = Boolean.TRUE.equals(d.getClassification().getIsAutomaticallyAssigned());
            confidence = d.getClassification().getConfidenceScore();
        }

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
                isFavorite,
                thumbnailUrl  // ✅ FIX: se propaga al record
        );
    }
}