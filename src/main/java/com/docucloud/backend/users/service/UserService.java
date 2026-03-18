package com.docucloud.backend.users.service;

import com.docucloud.backend.documents.service.CategoryService;
import com.docucloud.backend.common.service.EmailService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final EmailService emailService;

    public boolean existsById(Long userId) {
        return userRepository.existsById(userId);
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    public User findOrCreateFromGoogle(String email, String name, String picture) {
        boolean isNew = !userRepository.existsByEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createGoogleUser(email, name, picture));

        // Actualiza foto y nombre si cambiaron
        if (!isNew) {
            user.setName(name);
            user.setPhotoUrl(picture);
            userRepository.save(user);
        }

        return user;
    }

    private User createGoogleUser(String email, String name, String picture) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPhotoUrl(picture);
        user.setProvider(Provider.GOOGLE);
        user.setEnabled(true);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setMaxFavorites(100);
        user.setMaxFolders(20);
        user.setMaxTags(50);

        user = userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(Role.USER)
                .build();
        userRoleRepository.save(userRole);

        categoryService.createDefaultCategories(user);

        emailService.sendWelcome(email, name);

        return user;
    }


    // ── Perfil ────────────────────────────────────────────────────────────────

    public UserResponse getProfile(Long userId) {
        System.out.println(">>> UserService.getProfile(" + userId + ")");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        System.out.println("✅ Usuario encontrado: " + user.getEmail() + ", enabled=" + user.isEnabled() + ", roles=" + user.getRoles());

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
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está en uso");
            }
            user.setEmail(request.email());
        }

        return UserResponse.from(userRepository.save(user));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findById(userId);

        if (user.getProvider() == Provider.GOOGLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Usuarios Google no pueden cambiar contraseña local");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Contraseña actual incorrecta");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // ✅ Notificar al usuario que su contraseña fue cambiada
        emailService.sendPasswordChanged(user.getEmail());
    }

    // ── RF-27 Límites ─────────────────────────────────────────────────────────

    public boolean canCreateFolder(Long userId, Long currentFolders) {
        return currentFolders < findById(userId).getMaxFolders();
    }

    public boolean canCreateTag(Long userId, Long currentTags) {
        return currentTags < findById(userId).getMaxTags();
    }

    public boolean canAddFavorite(Long userId, Long currentFavorites) {
        return currentFavorites < findById(userId).getMaxFavorites();
    }

    public UserResponse updateLimits(Long adminId, Long targetUserId,
                                     Integer maxFolders, Integer maxTags, Integer maxFavorites) {
        User admin = findById(adminId);
        boolean isAdmin = admin.getRoles().stream()
                .anyMatch(ur -> ur.getRole() == Role.ADMIN);
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo admins pueden modificar límites");
        }

        User target = findById(targetUserId);
        if (maxFolders != null)   target.setMaxFolders(maxFolders);
        if (maxTags != null)      target.setMaxTags(maxTags);
        if (maxFavorites != null) target.setMaxFavorites(maxFavorites);

        return UserResponse.from(userRepository.save(target));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    public Page<UserResponse> getAllUsers(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search, search, pageable)
                : userRepository.findAll(pageable);
        return users.map(UserResponse::from);
    }

    public UserResponse updateRole(Long adminId, Long targetId, String roleName) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un admin no puede cambiar su propio rol");
        }

        Role role;
        try {
            role = Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rol inválido: " + roleName + ". Valores válidos: USER, ADMIN");
        }

        User user = findById(targetId);
        userRoleRepository.deleteByUserId(targetId);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .build();
        userRoleRepository.save(userRole);

        return UserResponse.from(findById(targetId));
    }

    public UserResponse toggleStatus(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes desactivar tu propia cuenta");
        }
        User user = findById(targetId);
        user.setEnabled(!user.isEnabled());
        return UserResponse.from(userRepository.save(user));
    }

    public void deleteUser(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes eliminar tu propia cuenta");
        }
        userRepository.deleteById(targetId);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado: " + userId));
    }
}
