package com.docucloud.backend.documents.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateMetadataRequest(

        @Size(min = 1, max = 255, message = "El nombre debe tener entre 1 y 255 caracteres")
        String fileName,

        Long categoryId,

        java.util.List<String> tags
) {}