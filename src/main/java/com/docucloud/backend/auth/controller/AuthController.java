package com.docucloud.backend.auth.controller;

import com.docucloud.backend.auth.dto.request.ForgotPasswordRequest;
import com.docucloud.backend.auth.dto.request.LoginRequest;
import com.docucloud.backend.auth.dto.request.RegisterRequest;
import com.docucloud.backend.auth.dto.request.ResetPasswordRequest;
import com.docucloud.backend.auth.dto.request.TokenRefreshRequest;
import com.docucloud.backend.auth.dto.response.JwtResponse;
import com.docucloud.backend.auth.dto.response.TokenRefreshResponse;
import com.docucloud.backend.auth.exception.TokenRefreshException;
import com.docucloud.backend.auth.service.AuthService;
import com.docucloud.backend.auth.service.PasswordResetService;
import com.docucloud.backend.auth.service.RefreshTokenService;
import com.docucloud.backend.common.dto.MessageResponse;
import com.docucloud.backend.common.service.RecaptchaService;
import com.docucloud.backend.config.security.jwt.JwtUtils;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.service.UserService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final PasswordResetService passwordResetService;
    private final RecaptchaService recaptchaService;

    @Value("${security.recaptcha.enabled:true}")   // ✅ ahora sí se inyecta
    private boolean recaptchaEnabled;

    @Value("${google.client-id}")                  // ✅ sacado del hardcode
    private String googleClientId;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService,
            UserService userService,
            JwtUtils jwtUtils,
            PasswordResetService passwordResetService,
            RecaptchaService recaptchaService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.passwordResetService = passwordResetService;
        this.recaptchaService = recaptchaService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (recaptchaEnabled) {
            boolean ok = recaptchaService.verify(request.getRecaptchaToken());
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new MessageResponse("reCAPTCHA inválido"));
            }
        }
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Usuario registrado"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(HttpServletRequest httpReq,
                                   @Valid @RequestBody LoginRequest request) {
        if (recaptchaEnabled) {
            boolean ok = recaptchaService.verify(request.getRecaptchaToken());
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new MessageResponse("reCAPTCHA inválido"));
            }
        }

        try {
            JwtResponse tokens = authService.login(request, httpReq.getRemoteAddr());
            return ResponseEntity.ok(tokens);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of(
                            "message", e.getReason() != null ? e.getReason() : "No se pudo iniciar sesión",
                            "reason", e.getStatusCode().value() == 429 ? "ACCOUNT_TEMPORARILY_LOCKED"
                                    : e.getStatusCode().value() == 403 ? "ACCOUNT_DISABLED"
                                    : "AUTH_ERROR"
                    ));

        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Correo o contraseña incorrectos.",
                            "reason", "BAD_CREDENTIALS"
                    ));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {
        return refreshTokenService.refreshAccessToken(request.getRefreshToken())
                .map(t -> ResponseEntity.ok(new TokenRefreshResponse(t.access(), t.refresh())))
                .orElseThrow(() -> new TokenRefreshException("Refresh token inválido o expirado"));
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        try {
            String idTokenString = body.get("credential");

            // ✅ googleClientId inyectado desde properties, no hardcodeado
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "ID Token de Google inválido"));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email   = payload.getEmail();
            String name    = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            User user = userService.findOrCreateFromGoogle(email, name, picture);
            JwtResponse tokens = authService.loginWithGoogle(user);
            return ResponseEntity.ok(tokens);

        } catch (Exception e) {
            log.error("Error autenticando con Google", e);   // ✅ log estructurado
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error autenticando con Google"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        if (recaptchaEnabled) {
            boolean ok = recaptchaService.verify(req.getRecaptchaToken());
            if (!ok) {
                return ResponseEntity.ok(new MessageResponse(
                        "Si el correo existe, enviaremos instrucciones para restablecer tu contraseña."));
            }
        }
        passwordResetService.requestPasswordReset(req.getEmail());
        return ResponseEntity.ok(new MessageResponse(
                "Si el correo existe, enviaremos instrucciones para restablecer tu contraseña."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Contraseña actualizada correctamente."));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails principal) {
        if (principal != null) {
            authService.logout(principal.getUsername());
        }
        return ResponseEntity.noContent().build();
    }
}