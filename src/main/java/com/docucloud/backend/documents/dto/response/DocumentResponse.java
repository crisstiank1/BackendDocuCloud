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
        Long folderId,
        Long categoryId,
        boolean isAutomaticallyAssigned,
        Instant createdAt,
        Instant updatedAt,
        boolean isFavorite
) {

    public static DocumentResponse from(Document d) {
        return from(d, false);
    }

    public static DocumentResponse from(Document d, boolean isFavorite) {
        if (d == null) return null;

        Long catId = d.getCategoryId(); // El ID simple de la columna en la tabla documents
        boolean auto = false;

        // Si la relación classification existe, sacamos los datos reales
        if (d.getClassification() != null) {
            catId = d.getClassification().getCategory().getId(); // Aseguramos el ID de la tabla intermedia
            auto = Boolean.TRUE.equals(d.getClassification().getIsAutomaticallyAssigned());
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
                d.getCreatedAt(),
                d.getUpdatedAt(),
                isFavorite
        );
    }
}