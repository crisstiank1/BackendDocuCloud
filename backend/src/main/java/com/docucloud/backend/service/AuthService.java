package com.docucloud.backend.service;

import com.docucloud.backend.model.RefreshToken; // Import RefreshToken model
import com.docucloud.backend.model.Role;
import com.docucloud.backend.model.User;
import com.docucloud.backend.model.UserRole;
import com.docucloud.backend.dtos.request.LoginRequest; // Corrected import path for dtos
import com.docucloud.backend.dtos.request.RegisterRequest; // Corrected import path for dtos
import com.docucloud.backend.dtos.response.JwtResponse; // Corrected import path for dtos
import com.docucloud.backend.repository.RoleRepository;
import com.docucloud.backend.repository.UserRepository;
import com.docucloud.backend.repository.UserRoleRepository;
import com.docucloud.backend.security.jwt.JwtUtils;
import com.docucloud.backend.security.services.UserDetailsImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Removed HashSet import as List is used
import java.util.List;
// Removed Set import
import java.util.stream.Collectors;

/**
 * Servicio para manejar la lógica de autenticación (registro y login).
 * Incluye ahora la gestión de Refresh Tokens.
 */
@Service
public class AuthService {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    RefreshTokenService refreshTokenService; // <-- Inject RefreshTokenService

    /**
     * Autentica un usuario, genera Access y Refresh Tokens.
     * @param loginRequest Datos de inicio de sesión.
     * @return JwtResponse con ambos tokens y detalles del usuario.
     */
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        // 1. Autenticar usando Spring Security
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        // 2. Establecer la autenticación
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Generar el Access Token JWT
        String jwt = jwtUtils.generateJwtToken(authentication);

        // 4. Obtener detalles del usuario
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // 5. Obtener los roles del usuario
        List<String> roles = userRoleRepository.findByUserAndIsActiveTrue(userRepository.findById(userDetails.getId()).orElseThrow())
                .stream()
                .map(userRole -> userRole.getRole().getName())
                .collect(Collectors.toList());

        // 6. Crear y guardar el Refresh Token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId()); // <-- NUEVO

        // 7. Construir y devolver la respuesta (AHORA INCLUYE REFRESH TOKEN)
        return new JwtResponse(jwt,
                refreshToken.getToken(), // <-- NUEVO: Pasa el refresh token
                userDetails.getId(),
                userDetails.getUsername(),
                roles);
    }

    /**
     * Registra un nuevo usuario en el sistema.
     * @param registerRequest Datos del nuevo usuario.
     * @return El usuario guardado.
     * @throws RuntimeException si el email ya está registrado o el rol USER no se encuentra.
     */
    @Transactional
    public User registerUser(RegisterRequest registerRequest) {
        // 1. Verificar si el email ya existe
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            // Consider using a custom exception or returning ResponseEntity from controller
            throw new RuntimeException("Error: Email is already in use!");
        }

        // 2. Crear el nuevo objeto User
        User user = new User(registerRequest.getName(),
                registerRequest.getEmail(),
                encoder.encode(registerRequest.getPassword()));

        // 3. Guardar el usuario
        User savedUser = userRepository.save(user);

        // 4. Asignar el rol 'USER' por defecto
        Role userRoleEntity = roleRepository.findByName("USER") // Changed variable name for clarity
                .orElseThrow(() -> new RuntimeException("Error: Role USER is not found. Ensure it exists in the database."));

        // 5. Crear la asignación UserRole
        UserRole newUserRoleAssignment = new UserRole(savedUser, userRoleEntity); // Changed variable name
        newUserRoleAssignment.setActive(true);

        // 6. Guardar la asignación
        userRoleRepository.save(newUserRoleAssignment);

        return savedUser;
    }
}
