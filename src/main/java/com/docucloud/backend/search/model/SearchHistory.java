package com.docucloud.backend.search.model;

import com.docucloud.backend.users.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "search_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String query;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public String getUserId() {
        return user != null ? user.getId().toString() : null;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreRemove
    public void preRemove() {
        // Cleanup
    }
}
