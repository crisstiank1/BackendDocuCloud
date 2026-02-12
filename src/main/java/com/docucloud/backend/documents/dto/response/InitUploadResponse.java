package com.docucloud.backend.documents.dto.response;

import java.time.Instant;

public record InitUploadResponse(Long documentId, String uploadUrl, Instant expiresAt, String s3Key) {}
