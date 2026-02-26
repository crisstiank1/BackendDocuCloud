package com.docucloud.backend.documents.dto.response;

public record PresignedUrlResponse(String downloadUrl, boolean writeAllowed) {}