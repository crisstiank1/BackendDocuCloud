package com.docucloud.backend.documents.model;

import com.docucloud.backend.tags.model.Tag;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_tags")
@IdClass(DocumentTagId.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentTag {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;
}
