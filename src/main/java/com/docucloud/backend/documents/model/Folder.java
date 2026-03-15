package com.docucloud.backend.documents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor    // Requerido por JPA
@AllArgsConstructor   // Requerido por el Builder
@Builder              // Permite crear carpetas de forma fluida
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;


    @Column(name = "full_path", nullable = false)
    private String fullPath;


    @Builder.Default
    @Column(name = "depth", nullable = false)
    private Integer depth = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        this.updatedAt = Instant.now();

        // Aseguramos que si no hay profundidad, empiece en 0
        if (this.depth == null) this.depth = 0;
    }

    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }
}