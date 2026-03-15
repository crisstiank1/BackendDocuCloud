package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.DocumentShare;
import com.docucloud.backend.documents.model.Permission;

import java.time.Instant;
import java.util.UUID;

public record ShareSummaryResponse(
        UUID id,
        Long documentId,
        String fileName,
        Permission permission,
        boolean hasPassword,
        boolean revoked,
        int usedCount,
        String recipientEmail,
        Instant expiresAt,
        Instant createdAt
) {
    public static ShareSummaryResponse from(DocumentShare share) {
        return new ShareSummaryResponse(
                share.getId(),
                share.getDocumentId(),
                null,
                share.getPermission(),
                share.getPasswordHash() != null,
                share.isRevoked(),
                share.getUsedCount(),
                share.getRecipientEmail(),
                share.getExpiresAt(),
                share.getCreatedAt()
        );
    }
}
