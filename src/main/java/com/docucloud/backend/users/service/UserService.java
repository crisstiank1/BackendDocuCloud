package com.docucloud.backend.users.service;

import com.docucloud.backend.documents.service.CategoryService;
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
import org.springframework.data.domain.Page;           // ✅ import 1
import org.springframework.data.domain.PageRequest;    // ✅ import 2
import org.springframework.data.domain.Pageable;       // ✅ import 3
import org.springframework.data.domain.Sort;           // ✅ import 4
import org.springframework.http.HttpStatus;
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
    // ✅ roleRepository ELIMINADO — Role es enum, no entidad JPA

    // ── Métodos sin cambios ──────────────────────────────────────────────────

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
                .password("google_" + UUID.randomUUID())
                .build();

        user = userRepository.save(user);

        UserRole userRole = UserRole.builder()
                .user(user)
                .role(Role.USER)
                .build();
        userRoleRepository.save(userRole);

        categoryService.createDefaultCategories(user);
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
    }

    // ── RF-27 Límites ────────────────────────────────────────────────────────

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

    // ── Admin: gestión de usuarios ───────────────────────────────────────────

    public Page<UserResponse> getAllUsers(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search, search, pageable)
                : userRepository.findAll(pageable);
        return users.map(UserResponse::from); // ✅ usa el factory method del record
    }

    public UserResponse updateRole(Long adminId, Long targetId, String roleName) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un admin no puede cambiar su propio rol");
        }

        // ✅ Role es enum — se parsea directamente, sin repositorio
        Role role;
        try {
            role = Role.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rol inválido: " + roleName + ". Valores válidos: USER, ADMIN");
        }

        User user = findById(targetId);

        // Reemplaza todos los roles existentes por el nuevo
        userRoleRepository.deleteByUserId(targetId);   // ← ver nota abajo

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
        return UserResponse.from(userRepository.save(user)); // ✅ usa from()
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
    // ✅ toResponse() ELIMINADO — era redundante y usaba builder inexistente
}
