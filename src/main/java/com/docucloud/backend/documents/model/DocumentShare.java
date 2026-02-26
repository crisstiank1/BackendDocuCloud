package com.docucloud.backend.documents.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_shares")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private Long sharedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    private String passwordHash;
    private Instant expiresAt;

    @Builder.Default
    private int usedCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    private Instant createdAt;
}
