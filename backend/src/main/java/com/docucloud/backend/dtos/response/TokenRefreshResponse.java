package com.docucloud.backend.dtos.response; // <-- Subpaquete para respuestas

/**
 * DTO para enviar la respuesta después de una renovación de token exitosa.
 * Contiene el nuevo Access Token y el Refresh Token original.
 */
public class TokenRefreshResponse {
    private String accessToken; // El nuevo Access Token generado
    private String refreshToken; // El Refresh Token que se usó para la solicitud
    private String tokenType = "Bearer"; // Tipo de token estándar

    // --- Constructor ---
    public TokenRefreshResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    // --- Getters y Setters ---

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
