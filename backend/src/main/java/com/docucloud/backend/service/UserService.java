package com.docucloud.backend.service;

import com.docucloud.backend.model.Role;
import com.docucloud.backend.model.User;
import com.docucloud.backend.model.UserRole;
import com.docucloud.backend.repository.UserRepository;
import com.docucloud.backend.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Busca el usuario por email, lo crea si no existe con datos de Google, y actualiza su perfil/foto.
     */
    @Transactional
    public User findOrCreateFromGoogle(String email, String name, String picture) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setEnabled(true);
            user.setName(name);
            user.setPhotoUrl(picture);
            // Asigna password dummy para cumplir la restricción NOT NULL de la DB
            user.setPassword("google_" + UUID.randomUUID());
            userRepository.save(user);

            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(Role.USER); // Siempre user; si luego quieres admins, deberás asignar aparte
            userRoleRepository.save(ur);
        } else {
            // Actualiza nombre/foto si cambian
            user.setName(name);
            user.setPhotoUrl(picture);
            userRepository.save(user);
        }
        return user;
    }
}
