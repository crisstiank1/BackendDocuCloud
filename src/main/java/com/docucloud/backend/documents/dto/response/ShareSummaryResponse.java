package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.DocumentShare;
import com.docucloud.backend.documents.model.Permission;

import java.time.Instant;
import java.util.UUID;

public record ShareSummaryResponse(
        UUID id,
        Long documentId,
        Permission permission,
        boolean hasPassword,
        boolean revoked,
        int usedCount,
        Instant expiresAt,
        Instant createdAt
) {
    public static ShareSummaryResponse from(DocumentShare share) {
        return new ShareSummaryResponse(
                share.getId(),
                share.getDocumentId(),
                share.getPermission(),
                share.getPasswordHash() != null,
                share.isRevoked(),
                share.getUsedCount(),
                share.getExpiresAt(),
                share.getCreatedAt()
        );
    }
}
