package com.docucloud.backend.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtils {

    @Value("${docucloud.app.jwtSecret}")
    private String jwtSecret;

    @Value("${docucloud.app.jwtExpirationMs}")
    private long jwtExpirationMs;

    @Value("${docucloud.app.jwtRefreshSecret}")
    private String jwtRefreshSecret;

    @Value("${docucloud.app.jwtRefreshExpirationMs}")
    private long jwtRefreshExpirationMs;

    private Key getAccessKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    private Key getRefreshKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtRefreshSecret));
    }

    // --- Generación de tokens ---

    // Para login: usa UserDetails completo (como ya tienes en AuthService)
    public String generateAccessToken(UserDetails user) {
        return generateAccessTokenFromSubject(user.getUsername());
    }

    // Para refresh: genera access solo a partir del subject (email)
    public String generateAccessTokenFromSubject(String subject) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(jwtExpirationMs);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(getAccessKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(jwtRefreshExpirationMs);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(getRefreshKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // --- Validación ---

    public boolean validateAccessToken(String token) {
        return validate(token, getAccessKey());
    }

    public boolean validateRefreshToken(String token) {
        return validate(token, getRefreshKey());
    }

    private boolean validate(String token, Key key) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // --- Lectura de claims ---

    public String extractUsername(String token, boolean refresh) {
        return extractClaim(token, Claims::getSubject, refresh);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver, boolean refresh) {
        Claims claims = getAllClaims(token, refresh);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaims(String token, boolean refresh) {
        Key key = refresh ? getRefreshKey() : getAccessKey();
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
