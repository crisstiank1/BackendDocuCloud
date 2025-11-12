package com.docucloud.backend.security.jwt;

import com.docucloud.backend.security.services.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {

            // --- INICIO DE LA CORRECCIÓN ---

            // 🔹 0. Ignorar SÓLO las rutas públicas de autenticación
            String uri = request.getRequestURI();

            // La lógica anterior (uri.startsWith("/api/auth/")) era demasiado amplia
            // y causaba que se saltara la validación para rutas protegidas como /api/auth/test/user.
            // Ahora, solo nos saltamos el filtro para las rutas públicas explícitas.
            if (uri.equals("/api/auth/login") ||
                    uri.equals("/api/auth/register") ||
                    uri.equals("/api/auth/refreshtoken")) {

                filterChain.doFilter(request, response);
                return;
            }

            // --- FIN DE LA CORRECIÓN ---


            // 🔹 1. Intentar extraer el JWT del encabezado Authorization
            String jwt = parseJwt(request);

            // 🔹 2. Si el token existe y es válido, autenticar al usuario
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                // 3. Obtener el nombre de usuario (email) del token
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                // 4. Cargar los detalles del usuario desde la base de datos
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 5. Crear el objeto de autenticación
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null, // No necesitamos las credenciales aquí
                                userDetails.getAuthorities() // Roles/Permisos
                        );

                // 6. Asociar detalles adicionales de la solicitud (IP, etc.)
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 7. Registrar la autenticación en el contexto de seguridad
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage(), e);
        }

        // 🔹 8. Continuar con la cadena de filtros
        filterChain.doFilter(request, response);
    }


    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}