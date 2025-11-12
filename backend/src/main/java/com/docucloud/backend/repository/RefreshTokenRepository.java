package com.docucloud.backend.repository;

import com.docucloud.backend.model.RefreshToken;
import com.docucloud.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // Needed for delete operations
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para la entidad RefreshToken.
 * Proporciona métodos para interactuar con la tabla 'refresh_tokens'.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Busca un RefreshToken por su valor de token (el UUID).
     *
     * @param token El UUID del refresh token a buscar.
     * @return Un Optional que contiene el RefreshToken si se encuentra.
     */
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser(User user);

    /**
     * Elimina todos los Refresh Tokens asociados a un usuario específico.
     * Útil, por ejemplo, cuando un usuario cierra sesión o cambia su contraseña,
     * invalidando todos sus tokens de refresco activos.
     * La anotación @Modifying es necesaria para indicar que esta consulta modifica datos.
     *
     * @param user El objeto User cuyos refresh tokens serán eliminados.
     * @return El número de registros eliminados.
     */
    @Modifying // Indicates that this query modifies data
    int deleteByUser(User user);

    // JpaRepository ya proporciona:
    // - save(RefreshToken entity)
    // - findById(Long id)
    // - delete(RefreshToken entity)
    // - etc.
}
