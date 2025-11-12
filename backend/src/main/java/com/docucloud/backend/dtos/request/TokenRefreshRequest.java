package com.docucloud.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para recibir la solicitud de renovación de token.
 * Contiene el refresh token enviado por el cliente.
 */
public class TokenRefreshRequest {

    @NotBlank(message = "Refresh token cannot be empty")
    private String refreshToken;

    // --- Getters y Setters ---
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}