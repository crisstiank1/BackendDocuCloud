package com.docucloud.backend.documents.dto.response;

import java.time.Instant;

public record DownloadUrlResponse(String downloadUrl, Instant expiresAt) {}
