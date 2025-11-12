package com.docucloud.backend.security.services;

import com.docucloud.backend.model.User;
import com.docucloud.backend.model.UserRole;
import com.docucloud.backend.repository.UserRepository;
import com.docucloud.backend.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementación de UserDetailsService.
 * Carga los detalles de un usuario desde la base de datos usando UserRepository.
 */
@Service // Marca esta clase como un servicio de Spring
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserRoleRepository userRoleRepository; // Para cargar los roles

    /**
     * Carga un usuario por su nombre de usuario (en nuestro caso, el email).
     * Este método es llamado por Spring Security durante el proceso de autenticación.
     *
     * @param username El email del usuario a cargar.
     * @return Un objeto UserDetails que representa al usuario encontrado.
     * @throws UsernameNotFoundException si el usuario no se encuentra en la base de datos.
     */
    @Override
    @Transactional // Asegura que las operaciones (buscar usuario y roles) se hagan en una transacción
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Busca el usuario por email en la base de datos
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username (email): " + username));

        // 2. Carga los roles asignados a ese usuario desde la tabla user_roles
        List<String> roles = userRoleRepository.findByUserAndIsActiveTrue(user)
                .stream()
                .map(userRole -> userRole.getRole().getName()) // Obtiene el nombre del rol
                .collect(Collectors.toList());

        // 3. Construye y devuelve el objeto UserDetailsImpl
        return UserDetailsImpl.build(user, roles);
    }
}