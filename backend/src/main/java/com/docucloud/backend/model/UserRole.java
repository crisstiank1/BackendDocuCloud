package com.docucloud.backend.model;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Entidad que representa la tabla de unión 'user_roles'.
 * Maneja la relación muchos a muchos entre User y Role.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Usaremos un ID autoincremental simple
    private Long id; // Aunque la tabla SQL usa clave compuesta, un ID simple es más fácil en JPA

    @ManyToOne(fetch = FetchType.LAZY) // Relación muchos-a-uno con User
    @JoinColumn(name = "user_id", nullable = false) // Columna FK en la tabla user_roles
    private User user;

    @ManyToOne(fetch = FetchType.LAZY) // Relación muchos-a-uno con Role
    @JoinColumn(name = "role_id", nullable = false) // Columna FK en la tabla user_roles
    private Role role;

    // --- Campos de Auditoría (opcionales pero útiles para saber cuándo se asignó) ---
    @Column(name = "assigned_by_id")
    private Long assignedById; // ID del usuario que asignó el rol

    @Column(name = "assignment_date", nullable = false, updatable = false)
    private Timestamp assignmentDate;

    @Column(name = "expiration_date")
    private Timestamp expirationDate; // Para roles temporales

    @Column(name = "is_active", columnDefinition = "boolean default true")
    private Boolean isActive = true;

    // --- Constructor ---
    public UserRole() {}

    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
        this.assignmentDate = Timestamp.from(Instant.now());
    }

    // --- Getters y Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getAssignedById() {
        return assignedById;
    }

    public void setAssignedById(Long assignedById) {
        this.assignedById = assignedById;
    }

    public Timestamp getAssignmentDate() {
        return assignmentDate;
    }

    public void setAssignmentDate(Timestamp assignmentDate) {
        this.assignmentDate = assignmentDate;
    }

    public Timestamp getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Timestamp expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    @PrePersist
    protected void onCreate() {
        assignmentDate = Timestamp.from(Instant.now());
    }
}