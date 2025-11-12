package com.docucloud.backend.dtos.response;

/**
 * DTO simple para enviar mensajes de respuesta genéricos (ej. éxito o error).
 * Utilizado por endpoints como /register.
 */
public class MessageResponse {
    private String message;

    public MessageResponse(String message) {
        this.message = message;
    }

    // --- Getters y Setters ---
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
