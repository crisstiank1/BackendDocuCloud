package com.docucloud.backend.security.oauth;

import com.docucloud.backend.model.User;
import com.docucloud.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Autowired
    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        // Solo permitir @gmail.com
        if (email == null || !email.endsWith("@gmail.com")) {
            throw new OAuth2AuthenticationException("Only Gmail accounts are allowed");
        }

        // Buscar o crear usuario
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPhotoUrl(picture);
                    newUser.setEnabled(true);
                    // Si quieres que todo Google login sea USER, asigna roles aquí
                    // Si ya existen roles, puedes omitir
                    return userRepository.save(newUser);
                });

        // Opcional: actualiza datos si cambiaron
        user.setName(name);
        user.setPhotoUrl(picture);
        userRepository.save(user);

        return oAuth2User; // se puede envolver si quieres roles de tu sistema
    }
}
