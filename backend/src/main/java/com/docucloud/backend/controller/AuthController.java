package com.docucloud.backend.controller;

import com.docucloud.backend.dtos.request.LoginRequest;
import com.docucloud.backend.dtos.request.RegisterRequest;
import com.docucloud.backend.dtos.request.TokenRefreshRequest;
import com.docucloud.backend.dtos.response.JwtResponse;
import com.docucloud.backend.dtos.response.MessageResponse;
import com.docucloud.backend.dtos.response.TokenRefreshResponse;
import com.docucloud.backend.exception.TokenRefreshException;
import com.docucloud.backend.service.AuthService;
import com.docucloud.backend.service.RefreshTokenService;
import com.docucloud.backend.service.UserService;
import com.docucloud.backend.security.jwt.JwtUtils;
import com.docucloud.backend.model.User;
import java.util.Collections;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService,
                          UserService userService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(201).body(new MessageResponse("Usuario registrado"));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        JwtResponse tokens = authService.login(request);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return refreshTokenService.refreshAccessToken(request.getRefreshToken())
                .map(t -> ResponseEntity.ok(new TokenRefreshResponse(t.access(), t.refresh())))
                .orElseThrow(() -> new TokenRefreshException("Refresh token inválido o expirado"));
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        try {
            String idTokenString = body.get("credential");
            String CLIENT_ID = "332858462648-clqf017jqb788uskjdfsa3hmi9af85mu.apps.googleusercontent.com";

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(CLIENT_ID))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "ID Token de Google inválido"));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            // Registrar o buscar usuario
            User user = userService.findOrCreateFromGoogle(email, name, picture);

            // Emitir JWT local (igual que login)
            JwtResponse tokens = authService.loginWithGoogle(user);

            return ResponseEntity.ok(tokens);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error autenticando con Google"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails principal) {
        if (principal != null) {
            authService.logout(principal.getUsername());
        }
        return ResponseEntity.noContent().build();
    }
}
