package com.docucloud.backend.auth.service;

import com.docucloud.backend.auth.dto.request.LoginRequest;
import com.docucloud.backend.auth.dto.request.RegisterRequest;
import com.docucloud.backend.auth.dto.response.JwtResponse;
import com.docucloud.backend.users.model.Role;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.model.UserRole;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.users.repository.UserRoleRepository;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.config.security.jwt.JwtUtils;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public AuthService(UserRepository userRepository,
                       UserRoleRepository userRoleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // Google login: password puede ser null o random
        user = userRepository.save(user);

        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(Role.USER);
        userRoleRepository.save(ur);
    }

    @Transactional
    public JwtResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        String access = jwtUtils.generateAccessToken(new UserDetailsImpl(user));
        String refresh = refreshTokenService.createRefreshToken(user);

        Set<String> roles = user.getRoles().stream().map(r -> r.getRole().name()).collect(Collectors.toSet());
        return new JwtResponse(access, refresh, user.getId(), user.getEmail(), roles);
    }

    /**
     * Login para usuario Google: NO pide credencial local.
     */
    @Transactional
    public JwtResponse loginWithGoogle(User user) {
        User managed = userRepository.findByEmailWithRoles(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));

        String access = jwtUtils.generateAccessToken(new UserDetailsImpl(managed));
        String refresh = refreshTokenService.createRefreshToken(managed);

        Set<String> roles = managed.getRoles().stream()
                .map(r -> r.getRole().name())
                .collect(Collectors.toSet());

        return new JwtResponse(access, refresh, managed.getId(), managed.getEmail(), roles);
    }


    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(refreshTokenService::revokeAllForUser);
    }
}
