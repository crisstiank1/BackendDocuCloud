package com.docucloud.backend.documents.dto.response;

public record ClassificationStatsResponse(
        long total,
        long classified,
        long pending,
        long failed,
        long categoriesCount
) {}