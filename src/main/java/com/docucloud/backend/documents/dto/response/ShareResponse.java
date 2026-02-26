package com.docucloud.backend.documents.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        String shareUrl,
        UUID shareId,
        Instant expiresAt       // ← Instant, no LocalDateTime
) {}