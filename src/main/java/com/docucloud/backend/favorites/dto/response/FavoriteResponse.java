package com.docucloud.backend.favorites.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FavoriteResponse {
    private Long       documentId;
    private String     documentName;
    private String     fileType;
    private Long   sizeBytes;
    private Long       folderId;
    private String     folderName;      // null si no hay carpeta o no se carga
    private LocalDateTime favoritedAt;
    private List<String> categoryNames;
}

