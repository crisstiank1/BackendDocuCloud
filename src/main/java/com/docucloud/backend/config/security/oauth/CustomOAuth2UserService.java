package com.docucloud.backend.config.security.oauth;

import com.docucloud.backend.documents.service.CategoryService;
import com.docucloud.backend.users.model.Provider;
import com.docucloud.backend.users.model.Role;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.model.UserRole;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.users.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;

    @Autowired
    public CustomOAuth2UserService(UserRepository userRepository,
                                   UserRoleRepository userRoleRepository,
                                   PasswordEncoder passwordEncoder,
                                   CategoryService categoryService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String email   = oAuth2User.getAttribute("email");
        String name    = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        if (email == null) {
            throw new OAuth2AuthenticationException("No se pudo obtener el email de la cuenta Google");
        }

        boolean isNew = !userRepository.existsByEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPhotoUrl(picture);
                    newUser.setEnabled(true);
                    newUser.setProvider(Provider.GOOGLE);
                    // ← NUEVO: password ficticio obligatorio (NOT NULL en BD)
                    newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    // ← NUEVO: límites por defecto
                    newUser.setMaxFavorites(100);
                    newUser.setMaxFolders(20);
                    newUser.setMaxTags(50);
                    return userRepository.save(newUser);
                });

        // Actualiza foto y nombre si cambiaron
        user.setName(name);
        user.setPhotoUrl(picture);
        userRepository.save(user);

        // ← NUEVO: asigna rol USER y categorías solo si es registro nuevo
        if (isNew) {
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(Role.USER);
            userRoleRepository.save(ur);

            categoryService.createDefaultCategories(user);
        }

        return oAuth2User;
    }
}
