package com.docucloud.backend.security.services;

import com.docucloud.backend.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementación de UserDetails de Spring Security.
 * Envuelve nuestra entidad User para proporcionar la información necesaria
 * para la autenticación y autorización.
 */
public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username; // En nuestro caso, será el email
    @JsonIgnore // Evita que la contraseña se serialice en respuestas JSON
    private String password;
    private Collection<? extends GrantedAuthority> authorities; // Los roles/permisos

    // Constructor privado, se usa el método build
    private UserDetailsImpl(Long id, String username, String password,
                            Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    /**
     * Método estático para construir un UserDetailsImpl a partir de una entidad User y sus roles.
     * @param user La entidad User de la base de datos.
     * @param roles La lista de nombres de roles del usuario (ej. ["ROLE_USER", "ROLE_ADMIN"]).
     * @return Una instancia de UserDetailsImpl.
     */
    public static UserDetailsImpl build(User user, List<String> roles) {
        // Convierte la lista de nombres de roles a objetos GrantedAuthority
        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)) // Importante prefijo ROLE_
                .collect(Collectors.toList());

        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(), // Usamos email como username
                user.getPasswordHash(),
                authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username; // Devuelve el email
    }

    // --- Métodos de estado de la cuenta (puedes personalizarlos según tu lógica) ---
    @Override
    public boolean isAccountNonExpired() {
        return true; // La cuenta nunca expira (puedes cambiar esto)
    }

    @Override
    public boolean isAccountNonLocked() {
        // Podrías usar el campo 'isLocked' de tu entidad User aquí
        return true; // La cuenta no está bloqueada (puedes cambiar esto)
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Las credenciales nunca expiran (puedes cambiar esto)
    }

    @Override
    public boolean isEnabled() {
        // Podrías usar un campo 'isEnabled' o verificar 'deletedAt' en tu entidad User
        return true; // La cuenta está habilitada (puedes cambiar esto)
    }

    // --- Métodos equals y hashCode (importantes para la gestión de sesiones) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // --- Getter adicional para el ID (útil en AuthService) ---
    public Long getId() {
        return id;
    }
}
