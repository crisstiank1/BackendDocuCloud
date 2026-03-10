package com.docucloud.backend.users.service;

import com.docucloud.backend.users.dto.request.ChangePasswordRequest;
import com.docucloud.backend.users.dto.request.UpdateProfileRequest;
import com.docucloud.backend.users.dto.response.UserResponse;
import com.docucloud.backend.users.model.Provider;
import com.docucloud.backend.users.model.Role;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.model.UserRole;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.users.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean existsById(Long userId) {
        return userRepository.existsById(userId);
    }

    public User findOrCreateFromGoogle(String email, String name, String picture) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> createGoogleUser(email, name, picture));
    }

    private User createGoogleUser(String email, String name, String picture) {
        User user = User.builder()
                .email(email)
                .name(name)
                .photoUrl(picture)
                .provider(Provider.GOOGLE)
                .enabled(true)
                .password("google_" + UUID.randomUUID())  // Dummy para local auth
                .build();

        user = userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(Role.USER)
                .build();
        userRoleRepository.save(userRole);

        return user;
    }

    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findById(userId));
    }

    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findById(userId);

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name());
        }

        if (request.email() != null && !request.email().isBlank()) {
            if (userRepository.existsByEmail(request.email()) &&
                    !request.email().equals(user.getEmail())) {
                throw new IllegalArgumentException("El correo ya está en uso");
            }
            user.setEmail(request.email());
        }

        return UserResponse.from(userRepository.save(user));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findById(userId);

        if (user.getProvider() == Provider.GOOGLE) {
            throw new IllegalArgumentException("Usuarios Google no pueden cambiar contraseña local");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Contraseña actual incorrecta");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // ⭐ RF-27 LÍMITES
    public boolean canCreateFolder(Long userId, Long currentFolders) {
        User user = findById(userId);
        return currentFolders < user.getMaxFolders();
    }

    public boolean canCreateTag(Long userId, Long currentTags) {
        User user = findById(userId);
        return currentTags < user.getMaxTags();
    }

    public boolean canAddFavorite(Long userId, Long currentFavorites) {
        User user = findById(userId);
        return currentFavorites < user.getMaxFavorites();
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    // Admin: Actualizar límites
    public UserResponse updateLimits(Long adminId, Long targetUserId, Integer maxFolders,
                                     Integer maxTags, Integer maxFavorites) {
        User admin = findById(adminId);
        if (!admin.getRoles().stream().anyMatch(ur -> ur.getRole() == Role.ADMIN)) {
            throw new SecurityException("Solo admins pueden modificar límites");
        }

        User target = findById(targetUserId);
        if (maxFolders != null) target.setMaxFolders(maxFolders);
        if (maxTags != null) target.setMaxTags(maxTags);
        if (maxFavorites != null) target.setMaxFavorites(maxFavorites);

        return UserResponse.from(userRepository.save(target));
    }
}
