package com.docucloud.backend.controller;

import com.docucloud.backend.model.User;
import com.docucloud.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(u -> ResponseEntity.ok(new MeResponse(u.getId(), u.getEmail(), u.isEnabled())))
                .orElse(ResponseEntity.notFound().build());
    }

    public record MeResponse(Long id, String email, boolean enabled) {}
}
