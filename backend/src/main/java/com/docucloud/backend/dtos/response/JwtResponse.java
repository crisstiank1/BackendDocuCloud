package com.docucloud.backend.dtos.response;

import java.util.List;

/**
 * DTO (Data Transfer Object) para enviar la respuesta después de un login exitoso.
 * Contiene el Access Token, Refresh Token y la información básica del usuario autenticado.
 */
public class JwtResponse {
    private String token; // Access Token
    private String type = "Bearer";
    private String refreshToken; // <-- NUEVO CAMPO AÑADIDO
    private Long id;
    private String username; // Generalmente el email
    private List<String> roles; // Lista de nombres de roles (ej. ["USER", "ADMIN"])

    // --- Constructor Actualizado ---
    // Ahora acepta el refreshToken como segundo argumento
    public JwtResponse(String accessToken, String refreshToken, Long id, String username, List<String> roles) {
        this.token = accessToken;
        this.refreshToken = refreshToken; // <-- ASIGNACIÓN DEL NUEVO CAMPO
        this.id = id;
        this.username = username;
        this.roles = roles;
    }

    // --- Getters y Setters (Añadir para refreshToken) ---

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRefreshToken() { // <-- NUEVO GETTER
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) { // <-- NUEVO SETTER
        this.refreshToken = refreshToken;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}

