package com.docucloud.backend.config.security.oauth;

import com.docucloud.backend.audit.service.AuditService;
import com.docucloud.backend.auth.service.RefreshTokenService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.config.security.jwt.JwtUtils;
import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final UserOAuth2RegistrationService registrationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email   = oAuth2User.getAttribute("email");
        String name    = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        registrationService.saveOrUpdateUser(email, name, picture);

        // findByEmailWithRoles carga los roles en la misma query — evita LazyInitializationException
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new RuntimeException(
                        "Error crítico: usuario no encontrado tras crearlo: " + email));

        // Verificar si el usuario está bloqueado
        if (!user.isEnabled()) {
            String redirectUrl = UriComponentsBuilder
                    .fromUriString(frontendUrl + "/auth/login")
                    .queryParam("error", "Tu cuenta ha sido bloqueada. Contacta al administrador.")
                    .build()
                    .toUriString();
            response.sendRedirect(redirectUrl);
            return;
        }

        // Registrar auditoría del login con Google
        ObjectNode details = objectMapper.createObjectNode();
        details.put("email", email);
        details.put("provider", "GOOGLE");
        auditService.logBusiness(
                user.getId(),
                "AUTH_LOGIN_GOOGLE",
                "Auth",
                user.getId(),
                true,
                details
        );

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String accessToken  = jwtUtils.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}