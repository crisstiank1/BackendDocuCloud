package com.docucloud.backend.dtos.request;

import jakarta.validation.constraints.NotBlank; // Para validaciones

public class LoginRequest {

    @NotBlank // Asegura que no sea nulo ni vacío
    private String username; // Puede ser email o un nombre de usuario

    @NotBlank
    private String password;

    // --- Getters y Setters ---
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}