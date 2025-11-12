package com.docucloud.backend.model;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Entidad que representa la tabla 'roles' en la base de datos.
 * Define los roles disponibles en la aplicación (ADMIN, USER, GUEST).
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100) // El nombre del rol debe ser único
    private String name; // Ej: "ADMIN", "USER", "GUEST"

    @Column(columnDefinition = "TEXT") // Permite descripciones largas
    private String description;

    @Column(name = "allowed_actions", columnDefinition = "TEXT[]") // Usa el tipo array de PostgreSQL
    private List<String> allowedActions; // Lista de acciones permitidas para este rol

    // --- Campos de Auditoría ---

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    // --- Constructor ---
    public Role() {}

    public Role(String name, String description, List<String> allowedActions) {
        this.name = name;
        this.description = description;
        this.allowedActions = allowedActions;
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = Timestamp.from(Instant.now());
    }

    // --- Getters y Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Métodos de ciclo de vida JPA para timestamps ---
    @PrePersist
    protected void onCreate() {
        createdAt = Timestamp.from(Instant.now());
        updatedAt = Timestamp.from(Instant.now());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Timestamp.from(Instant.now());
    }
}