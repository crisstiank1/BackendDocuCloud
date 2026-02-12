package com.docucloud.backend.auth.model;

import com.docucloud.backend.users.model.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_prt_token_hash", columnList = "tokenHash", unique = true),
                @Index(name = "idx_prt_user_id", columnList = "user_id")
        }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = true)
    private Instant usedAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(String tokenHash, User user, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public User getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }

    public boolean isUsed() { return usedAt != null; }
    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }
    public void markUsed(Instant now) { this.usedAt = now; }
}

