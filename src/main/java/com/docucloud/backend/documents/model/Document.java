package com.docucloud.backend.documents.model;

import com.docucloud.backend.tags.model.Tag;  // ← NUEVO IMPORT
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
@Table(name = "documents")
@Getter @Setter
public class Document {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="owner_user_id", nullable=false)
    private Long ownerUserId;

    @Column(name="file_name", nullable=false)
    private String fileName;

    @Column(name="mime_type")
    private String mimeType;

    @Column(name="size_bytes", nullable=false)
    private Long sizeBytes;

    @Column(name="file_hash")
    private String fileHash;

    @Column(name="s3_bucket", nullable=false)
    private String s3Bucket;

    @Column(name="s3_key", nullable=false, unique=true)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false)
    private DocumentStatus status = DocumentStatus.PENDING_UPLOAD;

    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt = Instant.now();

    @Column(name="updated_at", nullable=false)
    private Instant updatedAt = Instant.now();

    @Column(name="deleted_at")
    private Instant deletedAt;

    @Column(name = "folder_id")
    private Long folderId;

    // ← NUEVA RELACIÓN TAGS
    @ManyToMany
    @JoinTable(
            name = "document_tags",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @PrePersist
    void prePersist() { createdAt = Instant.now(); updatedAt = Instant.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
