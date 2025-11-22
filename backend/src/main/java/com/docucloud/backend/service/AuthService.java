package com.docucloud.backend.service;

import com.docucloud.backend.dtos.request.LoginRequest;
import com.docucloud.backend.dtos.request.RegisterRequest;
import com.docucloud.backend.dtos.response.JwtResponse;
import com.docucloud.backend.model.Role;
import com.docucloud.backend.model.User;
import com.docucloud.backend.model.UserRole;
import com.docucloud.backend.repository.UserRepository;
import com.docucloud.backend.repository.UserRoleRepository;
import com.docucloud.backend.security.jwt.JwtUtils;
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
        String access = jwtUtils.generateAccessToken(new com.docucloud.backend.security.services.UserDetailsImpl(user));
        String refresh = refreshTokenService.createRefreshToken(user);

        Set<String> roles = user.getRoles().stream().map(r -> r.getRole().name()).collect(Collectors.toSet());
        return new JwtResponse(access, refresh, user.getId(), user.getEmail(), roles);
    }

    /**
     * Login para usuario Google: NO pide credencial local.
     */
    @Transactional
    public JwtResponse loginWithGoogle(User user) {
        String access = jwtUtils.generateAccessToken(new com.docucloud.backend.security.services.UserDetailsImpl(user));
        String refresh = refreshTokenService.createRefreshToken(user);

        Set<String> roles = user.getRoles().stream().map(r -> r.getRole().name()).collect(Collectors.toSet());
        return new JwtResponse(access, refresh, user.getId(), user.getEmail(), roles);
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(refreshTokenService::revokeAllForUser);
    }
}
