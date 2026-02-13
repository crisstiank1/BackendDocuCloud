package com.docucloud.backend.storage.s3.dto;

import java.time.Instant;

public record PresignedUrlResponse(String url, Instant expiresAt, String method) {}
