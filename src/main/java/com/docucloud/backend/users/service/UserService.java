package com.docucloud.backend.users.service;

import com.docucloud.backend.audit.annotation.Audited;
import com.docucloud.backend.audit.service.AuditService;
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
import com.docucloud.backend.auth.repository.RefreshTokenRepository;
import com.docucloud.backend.auth.repository.PasswordResetTokenRepository;
import com.docucloud.backend.documents.repository.DocumentRepository;
import com.docucloud.backend.documents.repository.DocumentShareRepository;
import com.docucloud.backend.favorites.repository.FavoriteRepository;
import com.docucloud.backend.search.repository.SearchHistoryRepository;
import com.docucloud.backend.audit.repository.ActivityHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository               userRepository;
    private final UserRoleRepository           userRoleRepository;
    private final PasswordEncoder              passwordEncoder;
    private final CategoryService              categoryService;
    private final EmailService                 emailService;
    private final AuditService                 auditService;
    private final ObjectMapper                 objectMapper;
    private final RefreshTokenRepository       refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ActivityHistoryRepository    activityHistoryRepository;
    private final SearchHistoryRepository      searchHistoryRepository;
    private final FavoriteRepository           favoriteRepository;
    private final DocumentShareRepository      documentShareRepository;
    private final DocumentRepository           documentRepository;

    public boolean existsById(Long userId) {
        return userRepository.existsById(userId);
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    public User findOrCreateFromGoogle(String email, String name, String picture) {
        boolean isNew = !userRepository.existsByEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createGoogleUser(email, name, picture));

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
        return UserResponse.from(findById(userId));
    }

    @Audited(action = "UPDATE_USER", resourceType = "User", resourceIdArgIndex = 0)
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

        if (user.getProvider() == Provider.GOOGLE && !user.isHasLocalPassword()) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            user.setHasLocalPassword(true);
            userRepository.save(user);
            emailService.sendPasswordChanged(user.getEmail());
            return;
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Contraseña actual incorrecta");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
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

        User target = findById(targetId);
        boolean success = true;
        try {
            userRoleRepository.deleteByUserId(targetId);
            UserRole userRole = UserRole.builder().user(target).role(role).build();
            userRoleRepository.save(userRole);
            return UserResponse.from(findById(targetId));
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", target.getName());
            details.put("email", target.getEmail());
            details.put("role", roleName.toUpperCase());
            auditService.logBusiness(adminId, "CHANGE_USER_ROLE", "User", targetId, success, details);
        }
    }

    public UserResponse toggleStatus(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes desactivar tu propia cuenta");
        }

        User target = findById(targetId);
        boolean success = true;
        try {
            target.setEnabled(!target.isEnabled());
            return UserResponse.from(userRepository.save(target));
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", target.getName());
            details.put("email", target.getEmail());
            auditService.logBusiness(adminId, "UPDATE_USER", "User", targetId, success, details);
        }
    }

    // ✅ Cascada completa del remoto + audit manual del stash combinados
    public void deleteUser(Long adminId, Long targetId) {
        if (adminId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes eliminar tu propia cuenta");
        }

        User target = findById(targetId);
        log.info("🗑️ Iniciando eliminación de usuario id={} email={}", targetId, target.getEmail());

        boolean success = true;
        try {
            // ── 1. Tokens de sesión y recuperación ───────────────────────────
            refreshTokenRepository.deleteByUser_Id(targetId);
            passwordResetTokenRepository.deleteByUser_Id(targetId);

            // ── 2. Auditoría e historial ──────────────────────────────────────
            activityHistoryRepository.deleteByUserId(targetId);
            searchHistoryRepository.deleteByUser_Id(targetId);

            // ── 3. Favoritos ──────────────────────────────────────────────────
            favoriteRepository.deleteByUser_Id(targetId);

            // ── 4. Shares: revocar recibidos, borrar enviados ─────────────────
            documentShareRepository.revokeByRecipientEmail(target.getEmail());
            documentShareRepository.deleteBySharedByUserId(targetId);

            // ── 5. Documentos: soft delete ────────────────────────────────────
            documentRepository.softDeleteByOwnerUserId(targetId, Instant.now());

            // ── 6. Categorías del usuario (+ sus DocumentCategory) ────────────
            categoryService.deleteCategoriesByUserId(targetId);

            // ── 7. Usuario (UserRoles se borran por CASCADE ALL) ──────────────
            userRepository.deleteById(targetId);

            log.info("✅ Usuario eliminado correctamente id={} por adminId={}", targetId, adminId);
        } catch (Exception ex) {
            success = false;
            throw ex;
        } finally {
            // ✅ nombre y email capturados antes de borrar
            ObjectNode details = objectMapper.createObjectNode();
            details.put("name", target.getName());
            details.put("email", target.getEmail());
            auditService.logBusiness(adminId, "DELETE_USER", "User", targetId, success, details);
        }
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado: " + userId));
    }
}