package com.docucloud.backend.model;

import jakarta.persistence.*; // Usa jakarta.* para Spring Boot 3+
import java.sql.Timestamp;
import java.time.Instant; // Mejor para timestamps modernos

/**
 * Entidad que representa la tabla 'users' en la base de datos.
 * Incluye campos para la información del usuario y auditoría.
 */
@Entity
@Table(name = "users") // Nombre de la tabla en PostgreSQL
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // PostgreSQL usa secuencias (IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100) // Not null, longitud máxima
    private String name;

    @Column(nullable = false, unique = true, length = 150) // Not null, único, longitud
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_locked", columnDefinition = "boolean default false") // Valor por defecto en DB
    private Boolean isLocked = false;

    @Column(name = "registration_id")
    private Integer registrationId; // Corregido a Integer si es el tipo correcto

    // --- Campos de Auditoría ---

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt; // Para borrado suave

    @Column(name = "created_by_id")
    private Long createdById; // ID del usuario que creó este registro

    @Column(name = "updated_by_id")
    private Long updatedById; // ID del usuario que actualizó por última vez

    // --- Constructor (opcional pero útil) ---
    public User() {}

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        // Inicializar timestamps en la creación
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = Timestamp.from(Instant.now());
    }

    // --- Getters y Setters (Esenciales para JPA) ---
    // (Puedes generarlos automáticamente con tu IDE o usar Lombok)

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Boolean getLocked() {
        return isLocked;
    }

    public void setLocked(Boolean locked) {
        isLocked = locked;
    }

    public Integer getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Integer registrationId) {
        this.registrationId = registrationId;
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

    public Timestamp getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Timestamp deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public void setCreatedById(Long createdById) {
        this.createdById = createdById;
    }

    public Long getUpdatedById() {
        return updatedById;
    }

    public void setUpdatedById(Long updatedById) {
        this.updatedById = updatedById;
    }

    // --- Métodos de ciclo de vida JPA para actualizar timestamps (opcional) ---
    @PrePersist // Se ejecuta antes de guardar por primera vez
    protected void onCreate() {
        createdAt = Timestamp.from(Instant.now());
        updatedAt = Timestamp.from(Instant.now());
    }

    @PreUpdate // Se ejecuta antes de actualizar
    protected void onUpdate() {
        updatedAt = Timestamp.from(Instant.now());
    }
}
