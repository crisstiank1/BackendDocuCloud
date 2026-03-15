package com.docucloud.backend.users.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.users.dto.request.ChangePasswordRequest;
import com.docucloud.backend.users.dto.request.UpdateProfileRequest;
import com.docucloud.backend.users.dto.response.UserResponse;
import com.docucloud.backend.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;              // ✅ import faltante
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Helper centralizado — usado en todos los endpoints ───────────────────
    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
    }

    // ── Perfil propio (cualquier usuario autenticado) ────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(getUserId(auth)));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication auth) {
        return ResponseEntity.ok(userService.updateProfile(getUserId(auth), request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication auth) {
        userService.changePassword(getUserId(auth), request);
        return ResponseEntity.noContent().build();
    }

    // ── Límites ──────────────────────────────────────────────────────────────

    @PutMapping("/{id}/limits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateLimits(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> limits,
            Authentication auth) {
        return ResponseEntity.ok(userService.updateLimits(
                getUserId(auth),          // ✅ usa el helper, no casteo inline
                id,
                limits.get("maxFolders"),
                limits.get("maxTags"),
                limits.get("maxFavorites")
        ));
    }

    @GetMapping("/{id}/limits")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<Map<String, Object>> getLimits(
            @PathVariable Long id) {     // ✅ auth eliminado — ya no se usa

        // ✅ @PreAuthorize ya garantiza acceso; sin verificación manual duplicada
        // ✅ currentUserId eliminado del response — era info interna innecesaria
        var user = userService.findById(id);
        return ResponseEntity.ok(Map.of(
                "maxFolders",  user.getMaxFolders(),
                "maxTags",     user.getMaxTags(),
                "maxFavorites", user.getMaxFavorites()
        ));
    }

    // ── Admin: gestión de usuarios ───────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String search) {
        return ResponseEntity.ok(userService.getAllUsers(page, size, search));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateRole(
            @PathVariable Long id,
            @RequestParam String role,
            Authentication auth) {
        return ResponseEntity.ok(userService.updateRole(getUserId(auth), id, role));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> toggleStatus(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(userService.toggleStatus(getUserId(auth), id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            Authentication auth) {
        userService.deleteUser(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }
}
