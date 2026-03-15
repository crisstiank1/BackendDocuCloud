package com.docucloud.backend.documents.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class DocumentCategoryId implements Serializable {

    @Column(name = "document_id")
    private Long documentId;     // ← Long, no Integer

    @Column(name = "category_id")
    private Long categoryId;     // ← Long, no Integer
}
