package com.docucloud.backend.repository;

import com.docucloud.backend.model.User;
import com.docucloud.backend.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUser(User user);
}
