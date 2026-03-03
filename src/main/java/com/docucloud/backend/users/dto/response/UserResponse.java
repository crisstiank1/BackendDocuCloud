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
        Set<String> roles,
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getPhotoUrl(),
                u.getProvider(),
                u.getProvider() == Provider.LOCAL,
                u.getRoles().stream()
                        .map(r -> r.getRole().name())
                        .collect(Collectors.toSet()),
                u.getCreatedAt()
        );
    }
}
