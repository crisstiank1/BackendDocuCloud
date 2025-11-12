package com.docucloud.backend.controller;

// Imports de DTOs
import com.docucloud.backend.dtos.request.LoginRequest;
import com.docucloud.backend.dtos.request.RegisterRequest;
import com.docucloud.backend.dtos.request.TokenRefreshRequest;
import com.docucloud.backend.dtos.response.JwtResponse;
import com.docucloud.backend.dtos.response.MessageResponse;
import com.docucloud.backend.dtos.response.TokenRefreshResponse;

// Imports de Excepciones y Modelos
import com.docucloud.backend.exception.TokenRefreshException;
import com.docucloud.backend.model.RefreshToken;

// Imports de Servicios y Seguridad
import com.docucloud.backend.service.AuthService;
import com.docucloud.backend.service.RefreshTokenService;
import com.docucloud.backend.security.jwt.JwtUtils;
import com.docucloud.backend.security.services.UserDetailsImpl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Import para PreAuthorize
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para manejar las peticiones de autenticación (login, registro, refresh token).
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth") // Ruta base para este controlador
public class AuthController {

    @Autowired
    AuthService authService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    JwtUtils jwtUtils;

    /**
     * Endpoint para iniciar sesión.
     * @param loginRequest DTO con username y password.
     * @return ResponseEntity con JwtResponse (incluye Access y Refresh token).
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        JwtResponse jwtResponse = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(jwtResponse);
    }

    /**
     * Endpoint para registrar un nuevo usuario.
     * @param registerRequest DTO con name, email y password.
     * @return ResponseEntity con un mensaje de éxito.
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            authService.registerUser(registerRequest);
            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }


    /**
     * Endpoint para renovar el Access Token usando un Refresh Token.
     * @param request DTO que contiene el refreshToken.
     * @return ResponseEntity con TokenRefreshResponse (nuevo Access Token).
     */
    @PostMapping("/refreshtoken")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtUtils.generateTokenFromUsername(user.getEmail());
                    return ResponseEntity.ok(new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken,
                        "Refresh token is not in database or expired!"));
    }

    // --- ENDPOINT DE PRUEBA AÑADIDO ---
    /**
     * Endpoint de prueba protegido por rol.
     * Solo usuarios con ROLE_USER (o ROLE_ADMIN) pueden acceder.
     * @return Un mensaje de éxito.
     */
    @GetMapping("/test/user")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')") // Ajusta los roles según tu app
    public ResponseEntity<?> userAccessTest() {
        // Si llegas aquí, tu token fue validado exitosamente.
        return ResponseEntity.ok(new MessageResponse("¡Acceso a la ruta protegida (USER) exitoso!"));
    }

    /**
     * Endpoint para cerrar sesión (Logout).
     * Invalida el Refresh Token del usuario autenticado.
     * @param userDetails Los detalles del usuario obtenidos del Access Token.
     * @return Un mensaje de éxito.
     */
    @PostMapping("/logout")
    @PreAuthorize("hasRole('USER') or hasRole('MODERATOR') or hasRole('ADMIN')") // El usuario DEBE estar logueado para hacer logout
    public ResponseEntity<?> logoutUser(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        // Obtenemos el ID del usuario desde el token (AuthenticationPrincipal)
        Long userId = userDetails.getId();

        // Llamamos al servicio para borrar el token de refresco de la BD
        refreshTokenService.deleteByUserId(userId);

        return ResponseEntity.ok(new MessageResponse("Log out successful!"));
    }
    // (Puedes añadir más endpoints de prueba para otros roles si quieres)
    // @GetMapping("/test/admin")
    // @PreAuthorize("hasRole('ADMIN')")
    // public ResponseEntity<?> adminAccessTest() {
    //     return ResponseEntity.ok(new MessageResponse("¡Acceso a la ruta ADMIN exitoso!"));
    // }

}