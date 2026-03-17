package com.docucloud.backend.config.security.oauth;

import com.docucloud.backend.users.model.User;
import com.docucloud.backend.users.repository.UserRepository;
import com.docucloud.backend.config.security.jwt.JwtUtils;
import com.docucloud.backend.auth.security.UserDetailsImpl;
import com.docucloud.backend.auth.service.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Autowired
    public OAuth2SuccessHandler(JwtUtils jwtUtils,
                                UserRepository userRepository,
                                RefreshTokenService refreshTokenService) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        System.out.println(">>> OAuth2SuccessHandler ejecutándose <<<"); // ← agrega esto
        System.out.println(">>> Redirect URL destino: " + frontendUrl + "/oauth/callback");

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        // carga el User con roles en la misma query
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found after OAuth2 login"));

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        String accessToken  = jwtUtils.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendUrl + "/oauth/callback")
                .queryParam("token", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString();

        System.out.println(">>> Redirect URL FINAL: " + redirectUrl);

        response.sendRedirect(redirectUrl);
    }
}
