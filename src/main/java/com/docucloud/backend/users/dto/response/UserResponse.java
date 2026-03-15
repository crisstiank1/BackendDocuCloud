package com.docucloud.backend.users.dto.response;

import com.docucloud.backend.users.model.Provider;
import com.docucloud.backend.users.model.User;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String email,
        String name,
        String photoUrl,
        Provider provider,
        boolean hasPassword,
        boolean enabled,          // ← nuevo
        Set<String> roles,
        Instant createdAt,
        Integer maxFolders,       // ← nuevo (correcto aquí, no en el cuerpo)
        Integer maxTags,          // ← nuevo
        Integer maxFavorites      // ← nuevo
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getPhotoUrl(),
                u.getProvider(),
                u.getProvider() == Provider.LOCAL,
                u.isEnabled(),                          // ← nuevo
                u.getRoles().stream()
                        .map(r -> r.getRole().name())
                        .collect(Collectors.toSet()),
                u.getCreatedAt(),
                u.getMaxFolders(),                      // ← nuevo
                u.getMaxTags(),                         // ← nuevo
                u.getMaxFavorites()                     // ← nuevo
        );
    }
}
