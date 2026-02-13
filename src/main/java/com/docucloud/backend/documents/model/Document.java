package com.docucloud.backend.documents.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "documents")
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

    @PreUpdate
    public void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getS3Bucket() { return s3Bucket; }
    public void setS3Bucket(String s3Bucket) { this.s3Bucket = s3Bucket; }
    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
