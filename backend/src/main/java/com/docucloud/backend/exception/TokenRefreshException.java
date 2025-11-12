package com.docucloud.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción personalizada lanzada cuando un Refresh Token ha expirado o es inválido.
 * La anotación @ResponseStatus indica que debe devolver un error HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN) // Devuelve un 403 Forbidden al cliente
public class TokenRefreshException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TokenRefreshException(String token, String message) {
        super(String.format("Failed for [%s]: %s", token, message));
    }
}