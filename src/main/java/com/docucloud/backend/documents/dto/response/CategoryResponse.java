package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.Category;

public record CategoryResponse(
        Long   id,
        String name,
        String color,
        long   documentCount
) {
    public static CategoryResponse from(Category c, long documentCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getColor(), documentCount);
    }
}
