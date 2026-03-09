package com.docucloud.backend.auth.service;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.dto.request.LoginRequest;
import com.docucloud.backend.auth.dto.request.RegisterRequest;
import com.docucloud.backend.auth.dto.response.JwtResponse;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.common.security.BruteForceProtectionService;
import com.docucloud.backend.config.security.jwt.JwtUtils;
import com.docucloud.backend.users.model.Provider;
import com.docucloud.backend.users.model.Role;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.model.UserRole;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.users.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final BruteForceProtectionService bruteForceProtectionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository userRepository,
                       UserRoleRepository userRoleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       BruteForceProtectionService bruteForceProtectionService,
                       RefreshTokenService refreshTokenService,
                       AuditService auditService,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.bruteForceProtectionService = bruteForceProtectionService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider(Provider.LOCAL);
        user = userRepository.save(user);

        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(Role.USER);
        userRoleRepository.save(ur);

        // Auditoría: registro exitoso
        ObjectNode details = objectMapper.createObjectNode();
        details.put("email", user.getEmail());
        auditService.logBusiness(user.getId(), "AUTH_REGISTER", "Auth", user.getId(), true, details);
    }

    @Transactional
    public JwtResponse login(LoginRequest request, String ip) {
        String email = request.getEmail().toLowerCase().trim();

        String userKey = "user:" + email;
        String ipKey   = "ip:" + ip;

        if (bruteForceProtectionService.isLocked(userKey) || bruteForceProtectionService.isLocked(ipKey)) {
            // Auditoría: bloqueado por brute force
            ObjectNode details = objectMapper.createObjectNode();
            details.put("email", email);
            details.put("reason", "BRUTE_FORCE_LOCKED");
            auditService.logHttp(null, "AUTH_LOGIN_BLOCKED", "Auth", null, false, ip, null, details);

            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Intenta más tarde.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));

            bruteForceProtectionService.onSuccess(userKey);
            bruteForceProtectionService.onSuccess(ipKey);

        } catch (AuthenticationException ex) {
            bruteForceProtectionService.onFail(userKey);
            bruteForceProtectionService.onFail(ipKey);

            // Auditoría: login fallido (no guardamos el password, solo el email)
            ObjectNode details = objectMapper.createObjectNode();
            details.put("email", email);
            details.put("reason", ex.getClass().getSimpleName()); // BadCredentialsException etc.
            auditService.logHttp(null, "AUTH_LOGIN_FAILURE", "Auth", null, false, ip, null, details);

            throw ex;
        }

        User user = userRepository.findByEmail(email).orElseThrow();

        // ✅ NUEVO: Reiniciar contador de inactividad al login
        user.setLastActivityAt(Instant.now());
        userRepository.save(user);

        String access  = jwtUtils.generateAccessToken(new UserDetailsImpl(user));
        String refresh = refreshTokenService.createRefreshToken(user);

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getRole().name())
                .collect(Collectors.toSet());

        // Auditoría: login exitoso
        ObjectNode details = objectMapper.createObjectNode();
        details.put("email", email);
        auditService.logHttp(user.getId(), "AUTH_LOGIN_SUCCESS", "Auth", user.getId(), true, ip, null, details);

        return new JwtResponse(access, refresh, user.getId(), user.getEmail(), roles, user.getName());
    }


    @Transactional
    public JwtResponse loginWithGoogle(User user) {
        User managed = userRepository.findByEmailWithRoles(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));

        String access  = jwtUtils.generateAccessToken(new UserDetailsImpl(managed));
        String refresh = refreshTokenService.createRefreshToken(managed);

        Set<String> roles = managed.getRoles().stream()
                .map(r -> r.getRole().name())
                .collect(Collectors.toSet());

        // Auditoría: login con Google
        ObjectNode details = objectMapper.createObjectNode();
        details.put("email", managed.getEmail());
        details.put("provider", "GOOGLE");
        auditService.logBusiness(managed.getId(), "AUTH_LOGIN_GOOGLE", "Auth", managed.getId(), true, details);

        return new JwtResponse(access, refresh, managed.getId(), managed.getEmail(), roles, managed.getName());
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            refreshTokenService.revokeAllForUser(user);

            // Auditoría: logout
            ObjectNode details = objectMapper.createObjectNode();
            details.put("email", email);
            auditService.logBusiness(user.getId(), "AUTH_LOGOUT", "Auth", user.getId(), true, details);
        });
    }
}
