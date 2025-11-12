package com.docucloud.backend.repository;

import com.docucloud.backend.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para la entidad Role.
 * Proporciona métodos CRUD y consultas personalizadas para la tabla 'roles'.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Busca un rol por su nombre.
     * Útil para encontrar roles específicos como "ADMIN", "USER", "GUEST".
     *
     * @param name El nombre del rol a buscar.
     * @return Un Optional que contiene el Role si se encuentra.
     */
    Optional<Role> findByName(String name);

    /**
     * Verifica si existe un rol con el nombre dado.
     *
     * @param name El nombre del rol a verificar.
     * @return true si existe un rol con ese nombre, false en caso contrario.
     */
    Boolean existsByName(String name);
}
