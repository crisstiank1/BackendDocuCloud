package com.docucloud.backend.config.security.oauth;

import com.docucloud.backend.auth.service.RefreshTokenService;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.config.security.jwt.JwtUtils;
import com.docucloud.backend.users.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
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
    private final UserOAuth2RegistrationService registrationService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        System.out.println(">>> OAuth2SuccessHandler ejecutándose");

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email   = oAuth2User.getAttribute("email");
        String name    = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        System.out.println(">>> Email: " + email);

        // ✅ Guardar/actualizar usuario aquí directamente
        User user = registrationService.saveOrUpdateUser(email, name, picture);

        System.out.println(">>> Usuario guardado: " + user.getId());

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String accessToken  = jwtUtils.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString();

        System.out.println(">>> Redirigiendo a: " + redirectUrl);
        response.sendRedirect(redirectUrl);
    }
}