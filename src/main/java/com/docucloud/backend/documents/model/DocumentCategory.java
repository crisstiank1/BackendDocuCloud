package com.docucloud.backend.documents.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_categories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentCategory {

    @EmbeddedId
    private DocumentCategoryId id;

    @Column(name = "is_automatically_assigned", nullable = false)
    private Boolean isAutomaticallyAssigned = false;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // Helpers — ahora Long consistente con Category.id y Document.id
    public Long getDocumentId() { return id != null ? id.getDocumentId() : null; }
    public Long getCategoryId() { return id != null ? id.getCategoryId() : null; }
}
