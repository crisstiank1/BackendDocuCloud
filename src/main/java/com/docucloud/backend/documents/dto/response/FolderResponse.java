package com.docucloud.backend.documents.dto.response;

import com.docucloud.backend.documents.model.Folder;
import java.time.Instant;

public record FolderResponse(
        Long id,
        String name,
        Long parentId,
        Instant createdAt,
        Instant updatedAt
) {
    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }
}
