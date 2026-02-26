package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.Folder;
import java.time.Instant;

public record FolderResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
