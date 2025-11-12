package com.docucloud.backend.repository;

import com.docucloud.backend.model.Role;
import com.docucloud.backend.model.User;
import com.docucloud.backend.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la entidad UserRole.
 * Proporciona métodos para interactuar con la tabla de unión 'user_roles'.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * Encuentra todas las asignaciones de roles para un usuario específico.
     * Útil para obtener todos los roles de un usuario al momento del login o para verificar permisos.
     *
     * @param user El objeto User por el cual filtrar.
     * @return Una lista de UserRole que pertenecen a ese usuario.
     */
    List<UserRole> findByUser(User user);

    /**
     * Encuentra todas las asignaciones de roles para un rol específico.
     * Útil si necesitas saber qué usuarios tienen un rol particular (ej. todos los ADMIN).
     *
     * @param role El objeto Role por el cual filtrar.
     * @return Una lista de UserRole asociados a ese rol.
     */
    List<UserRole> findByRole(Role role);

    /**
     * Busca una asignación específica entre un usuario y un rol.
     * Útil para verificar si un usuario ya tiene un rol asignado antes de intentar agregarlo de nuevo.
     *
     * @param user El objeto User.
     * @param role El objeto Role.
     * @return Un Optional que contiene el UserRole si la asignación existe.
     */
    Optional<UserRole> findByUserAndRole(User user, Role role);

    /**
     * Encuentra todas las asignaciones de roles activas para un usuario.
     * Filtra las asignaciones donde isActive es true.
     *
     * @param user El objeto User.
     * @return Una lista de UserRole activos para ese usuario.
     */
    List<UserRole> findByUserAndIsActiveTrue(User user);

}
