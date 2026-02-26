package com.docucloud.backend.documents.dto.response;

import java.time.Instant;

public record ShareAccessResponse(
        String downloadUrl,
        Instant expiresAt,
        boolean writeAllowed
) {}
