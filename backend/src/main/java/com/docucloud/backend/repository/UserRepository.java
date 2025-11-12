package com.docucloud.backend.repository;

import com.docucloud.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para la entidad User.
 * Proporciona métodos CRUD y consultas personalizadas para interactuar con la tabla 'users'.
 * Extiende JpaRepository para obtener la implementación automática de Spring Data JPA.
 */
@Repository // Marca esta interfaz como un componente de repositorio de Spring
public interface UserRepository extends JpaRepository<User, Long> { // <User, Long> -> Entidad User, Tipo de la clave primaria (ID) es Long

    /**
     * Busca un usuario por su dirección de correo electrónico.
     * Spring Data JPA implementará automáticamente esta consulta basándose en el nombre del método.
     *
     * @param email La dirección de correo electrónico a buscar.
     * @return Un Optional que contiene el User si se encuentra, o un Optional vacío si no existe.
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica si existe un usuario con la dirección de correo electrónico dada.
     * Spring Data JPA también implementa esto automáticamente.
     * Es más eficiente que findByEmail si solo necesitas saber si existe.
     *
     * @param email La dirección de correo electrónico a verificar.
     * @return true si existe un usuario con ese correo, false en caso contrario.
     */
    Boolean existsByEmail(String email);

    // Spring Data JPA ya proporciona métodos como:
    // - save(User entity) -> Guarda o actualiza un usuario
    // - findById(Long id) -> Busca un usuario por ID
    // - findAll() -> Obtiene todos los usuarios
    // - deleteById(Long id) -> Elimina un usuario por ID
    // ... y muchos más.
}