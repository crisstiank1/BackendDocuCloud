package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.DocumentShare;
import com.docucloud.backend.documents.model.Document;
import com.docucloud.backend.users.model.User;

import java.time.Instant;
import java.util.UUID;

public record SharedWithMeResponse(
        UUID shareId,
        Long documentId,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String sharedByName,
        String sharedByEmail,
        String permission,
        boolean isExpired,
        Instant expiresAt,
        Instant sharedAt,
        int usedCount
) {
    public static SharedWithMeResponse from(DocumentShare share, Document doc, User sharedBy) {
        return new SharedWithMeResponse(
                share.getId(),
                doc.getId(),
                doc.getFileName(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                sharedBy.getName(),
                sharedBy.getEmail(),
                share.getPermission().name(),
                share.getExpiresAt() != null && share.getExpiresAt().isBefore(Instant.now()),
                share.getExpiresAt(),
                share.getCreatedAt(),
                share.getUsedCount()
        );
    }
}
