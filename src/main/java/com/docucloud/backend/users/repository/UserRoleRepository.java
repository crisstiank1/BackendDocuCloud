package com.docucloud.backend.users.repository;

import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUser(User user);
}
