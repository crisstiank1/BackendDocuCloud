package com.docucloud.backend.config.security.oauth;

import com.docucloud.backend.documents.service.CategoryService;
import com.docucloud.backend.users.model.Provider;
import com.docucloud.backend.users.model.Role;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.model.UserRole;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.users.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserOAuth2RegistrationService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User saveOrUpdateUser(String email, String name, String picture) {
        boolean isNew = !userRepository.existsByEmail(email);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setName(name);
                    newUser.setPhotoUrl(picture);
                    newUser.setEnabled(true);
                    newUser.setProvider(Provider.GOOGLE);
                    newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    newUser.setMaxFavorites(100);
                    newUser.setMaxFolders(20);
                    newUser.setMaxTags(50);
                    return userRepository.save(newUser);
                });

        user.setName(name);
        user.setPhotoUrl(picture);
        userRepository.save(user);

        if (isNew) {
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(Role.USER);
            userRoleRepository.save(ur);
            categoryService.createDefaultCategories(user);
        }

        return user;
    }
}