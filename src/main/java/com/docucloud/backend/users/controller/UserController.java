package com.docucloud.backend.users.controller;

import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.users.dto.request.ChangePasswordRequest;
import com.docucloud.backend.users.dto.request.UpdateProfileRequest;
import com.docucloud.backend.users.dto.response.UserResponse;
import com.docucloud.backend.users.service.UserService;
import jakarta.validation.Valid;
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

    private Long getUserId(Authentication auth) {
        return ((UserDetailsImpl) auth.getPrincipal()).getId();
    }

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

    @PutMapping("/{id}/limits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateLimits(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> limits,
            Authentication auth) {

        Long adminId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        return ResponseEntity.ok(userService.updateLimits(
                adminId, id,
                limits.get("maxFolders"),
                limits.get("maxTags"),
                limits.get("maxFavorites")
        ));
    }
}
