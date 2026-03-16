package com.docucloud.backend.users.repository;

import com.docucloud.backend.users.model.User;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
        select distinct u
        from User u
        left join fetch u.roles
        where u.email = :email
    """)
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    // Búsqueda para el panel admin (paginada)
    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastActivityAt = :now WHERE u.email = :email")
    void updateLastActivity(@Param("email") String email, @Param("now") Instant now);

    @Query("SELECT u.lastActivityAt FROM User u WHERE u.email = :email")
    Instant findLastActivityByEmail(@Param("email") String email);

}
