package com.docucloud.backend.model;

import jakarta.persistence.*;
import java.sql.Timestamp; // Use java.sql.Timestamp for compatibility with DB TIMESTAMP
import java.time.Instant;

/**
 * Entidad que representa la tabla 'refresh_tokens' en la base de datos.
 * Almacena los refresh tokens generados para los usuarios.
 */
@Entity
@Table(name = "refresh_tokens") // Nombre de la tabla en PostgreSQL
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne // Cada refresh token pertenece a un solo usuario
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) // Clave foránea a la tabla 'users'
    private User user;

    @Column(nullable = false, unique = true) // El token debe ser único
    private String token; // El UUID del refresh token

    @Column(name = "expiry_date", nullable = false) // Fecha de expiración del token
    private Timestamp expiryDate;

    // --- Constructor ---
    public RefreshToken() {}

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Timestamp getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Timestamp expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * Helper method to set expiry date using Instant.
     * @param expiryInstant The Instant representing the expiry time.
     */
    public void setExpiryDateFromInstant(Instant expiryInstant) {
        this.expiryDate = Timestamp.from(expiryInstant);
    }
}
