package com.docucloud.backend.config;

import com.docucloud.backend.config.security.jwt.AuthEntryPointJwt;
import com.docucloud.backend.config.security.jwt.AuthTokenFilter;
import com.docucloud.backend.config.security.jwt.InactivityFilter;
import com.docucloud.backend.config.security.oauth.CustomOAuth2UserService;
import com.docucloud.backend.config.security.oauth.OAuth2SuccessHandler;
import com.docucloud.backend.auth.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;      // ✅ nuevo
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthEntryPointJwt unauthorizedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final InactivityFilter inactivityFilter;

    // ✅ Lee orígenes CORS desde application.properties
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Autowired
    public SecurityConfig(
            AuthTokenFilter authTokenFilter,
            InactivityFilter inactivityFilter,
            UserDetailsServiceImpl userDetailsService,
            AuthEntryPointJwt unauthorizedHandler,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.authTokenFilter = authTokenFilter;
        this.inactivityFilter = inactivityFilter;
        this.userDetailsService = userDetailsService;
        this.unauthorizedHandler = unauthorizedHandler;
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedHandler)   // 401 — no autenticado
                        .accessDeniedHandler(accessDeniedHandler())      // ✅ 403 — sin permisos
                )
                .authorizeHttpRequests(auth -> auth
                        // Públicos
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/health/**").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/oauth2/**").permitAll()
                        .requestMatchers("/api/dev/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/shares/*/access").permitAll()

                        // ✅ Admin — doble protección: URL + @PreAuthorize en el controller
                        .requestMatchers(HttpMethod.GET,    "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/api/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/users/{id}/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/users/{id}/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/users/{id}/limits").hasRole("ADMIN")

                        // ✅ /api/users/me — redundante eliminado, anyRequest lo cubre
                        // El resto requiere autenticación
                        .anyRequest().authenticated()
                )
                .userDetailsService(userDetailsService)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(info -> info.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .defaultSuccessUrl("/api/oauth2/success", true)
                );

        // Orden: 1° valida JWT → 2° revisa inactividad
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(inactivityFilter, AuthTokenFilter.class);

        return http.build();
    }

    // ✅ Handler para 403 — responde JSON consistente con el resto de la API
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            var body = Map.of(
                    "status",    403,
                    "error",     "Forbidden",
                    "message",   "No tienes permisos para realizar esta acción",
                    "path",      request.getRequestURI(),
                    "timestamp", Instant.now().toString()
            );

            new ObjectMapper().writeValue(response.getWriter(), body);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);          // ✅ desde properties
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
