package com.docucloud.backend.service;

import com.docucloud.backend.exception.TokenRefreshException;
import com.docucloud.backend.model.RefreshToken;
import com.docucloud.backend.model.User;
import com.docucloud.backend.repository.RefreshTokenRepository;
import com.docucloud.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


/**
 * Servicio para gestionar los Refresh Tokens.
 * Encapsula la lógica de creación, verificación y eliminación de tokens.
 */
@Service
public class RefreshTokenService {
    @Value("${docucloud.app.jwtRefreshExpirationMs}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Crea o actualiza un RefreshToken para un usuario específico.
     * Si el usuario ya tiene un token, este será actualizado.
     * Si no, se creará uno nuevo.
     *
     * @param userId El ID del usuario para el cual crear/actualizar el token.
     * @return El RefreshToken guardado (creado o actualizado).
     */
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        // Busca al usuario por ID
        User user = userRepository.findById(userId).orElseThrow(() ->
                new RuntimeException("Error: User not found with id " + userId));

        // --- INICIO DE LA CORRECCIÓN ---

        // 1. Busca si ya existe un token para este usuario.
        //    (Asegúrate de que tu RefreshTokenRepository tenga este método.
        //    Si se llama 'findByUserId', cámbialo. 'findByUser' es estándar en JPA).
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUser(user);

        RefreshToken refreshToken;

        if (existingToken.isPresent()) {
            // 2. Si SÍ existe, actualiza el token existente
            refreshToken = existingToken.get();
            refreshToken.setExpiryDateFromInstant(Instant.now().plusMillis(refreshTokenDurationMs)); // Renueva la expiración
            refreshToken.setToken(UUID.randomUUID().toString()); // Genera un nuevo valor de token
        } else {
            // 3. Si NO existe, crea un nuevo objeto RefreshToken
            refreshToken = new RefreshToken();
            refreshToken.setUser(user);
            refreshToken.setExpiryDateFromInstant(Instant.now().plusMillis(refreshTokenDurationMs));
            refreshToken.setToken(UUID.randomUUID().toString());
        }

        // 4. Guarda el token (JPA se encargará de hacer un UPDATE o INSERT)
        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken;

        // --- FIN DE LA CORRECCIÓN ---
    }

    /**
     * Verifica si un RefreshToken ha expirado.
     * ... (El resto de tu clase es correcto) ...
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().before(Timestamp.from(Instant.now()))) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
    public int deleteByUserId(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new RuntimeException("Error: User not found with id " + userId));
        return refreshTokenRepository.deleteByUser(user);
    }
}
