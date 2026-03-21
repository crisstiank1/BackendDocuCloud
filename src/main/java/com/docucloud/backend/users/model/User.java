package com.docucloud.backend.users.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hasLocalPassword = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<UserRole> roles = new HashSet<>();

    // Social login
    @Column(length = 180)
    private String name;

    @Column(length = 512)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'LOCAL'")
    private Provider provider = Provider.LOCAL;

    @Column
    private Instant lastActivityAt;

    // ⭐ LÍMITES RF-27
    @Column(nullable = false, columnDefinition = "integer default 20")
    private Integer maxFolders = 20;

    @Column(nullable = false, columnDefinition = "integer default 50")
    private Integer maxTags = 50;

    @Column(nullable = false, columnDefinition = "integer default 100")
    private Integer maxFavorites = 100;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Convenience methods RF-27
    public boolean canCreateFolder(long currentFolders) {
        return currentFolders < getMaxFolders();
    }

    public boolean canCreateTag(long currentTags) {
        return currentTags < getMaxTags();
    }

    public boolean canAddFavorite(long currentFavorites) {
        return currentFavorites < getMaxFavorites();
    }

}
