package com.docucloud.backend.documents.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InitUploadRequest(
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @NotNull @Min(1) @Max(52428800) Long sizeBytes
) {}
