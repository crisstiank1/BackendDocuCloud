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
        Instant createdAt,
        Instant updatedAt,
        boolean isFavorite          // ← NUEVO
) {
    /** Factory sin favoritos – mantiene compatibilidad con código existente */
    public static DocumentResponse from(Document d) {
        return from(d, false);
    }

    /** Factory enriquecido – usado al listar con contexto de usuario */
    public static DocumentResponse from(Document d, boolean isFavorite) {
        if (d == null) return null;
        return new DocumentResponse(
                d.getId(),
                d.getFileName()  != null ? d.getFileName()  : "",
                d.getMimeType()  != null ? d.getMimeType()  : "",
                d.getSizeBytes() != null ? d.getSizeBytes() : 0L,
                d.getFileHash()  != null ? d.getFileHash()  : "",
                d.getStatus()    != null ? d.getStatus()    : DocumentStatus.PENDING_UPLOAD,
                d.getFolderId(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                isFavorite
        );
    }
}
