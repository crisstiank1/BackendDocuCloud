package com.docucloud.backend.users.repository;

import com.docucloud.backend.users.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
