package com.docucloud.backend.documents.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedByMeResponse {

    private Long   documentId;
    private String fileName;
    private String mimeType;
    private Long   sizeBytes;
    private String createdAt;
    private String thumbnailUrl; // null si no es imagen

    private List<ShareSummary> shares;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ShareSummary {
        private String  shareId;          // UUID como String
        private String  recipientEmail;
        private String  permission;       // "READ" | "WRITE"
        private boolean hasPassword;
        private int     usedCount;
        private String  expiresAt;        // null si no expira
        private String  createdAt;
    }
}