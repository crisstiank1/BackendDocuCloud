package com.docucloud.backend.documents.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompleteUploadRequest(
        @NotNull @Min(1) Long sizeBytes,
        String fileHash
) {}
