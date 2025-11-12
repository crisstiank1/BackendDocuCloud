package com.docucloud.backend.security.jwt;

import com.docucloud.backend.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException; // Correct import for SignatureException
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey; // Use javax.crypto for SecretKey
import java.util.Date;
import java.util.UUID; // For generating unique refresh tokens

/**
 * Utility class for handling JWT operations (Generation, Validation, Extraction).
 * Now includes support for Access Tokens and Refresh Tokens.
 */
@Component // Marks this class as a Spring component for injection
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    // Secret key for Access Tokens (read from application.properties)
    @Value("${docucloud.app.jwtSecret}")
    private String jwtSecret;

    // Expiration time for Access Tokens (e.g., 1 hour in ms)
    @Value("${docucloud.app.jwtExpirationMs}")
    private int jwtExpirationMs;

    // Secret key for Refresh Tokens (MUST BE DIFFERENT from jwtSecret!)
    @Value("${docucloud.app.jwtRefreshSecret}")
    private String jwtRefreshSecret; // Ensure this is defined in application.properties

    // Expiration time for Refresh Tokens (e.g., 7 days in ms)
    @Value("${docucloud.app.jwtRefreshExpirationMs}")
    private int jwtRefreshExpirationMs; // Ensure this is defined in application.properties

    /**
     * Generates an Access Token JWT from authentication information.
     * @param authentication Spring Security Authentication object.
     * @return The Access Token JWT as a String.
     */
    public String generateJwtToken(Authentication authentication) {
        // Gets details of the authenticated user principal
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return generateTokenFromUsername(userPrincipal.getUsername());
    }

    /**
     * Generates an Access Token JWT from the username (email).
     * @param username The user's email.
     * @return The Access Token JWT as a String.
     */
    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .setSubject(username) // Sets the 'subject' (usually email)
                .setIssuedAt(new Date()) // Issue date
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs)) // Expiration date
                .signWith(getAccessKey(), SignatureAlgorithm.HS512) // Signs the token with the secret key and HS512 algorithm
                .compact(); // Builds the final token
    }

    /**
     * Generates a unique Refresh Token.
     * This token usually doesn't contain user info, just a unique ID.
     * @return The Refresh Token as a String (UUID).
     */
    public String generateRefreshToken() {
        // Generates a UUID as the Refresh Token. Simple and hard to guess.
        return UUID.randomUUID().toString();
    }


    /**
     * Gets the secret key for Access Tokens.
     * @return SecretKey.
     */
    private SecretKey getAccessKey() {
        // Decodes the BASE64 secret from application.properties
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    /**
     * Gets the secret key for Refresh Tokens (IF you decide to use JWTs for Refresh Tokens later).
     * @return SecretKey.
     */
    // private SecretKey getRefreshKey() {
    //    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtRefreshSecret));
    // }

    /**
     * Extracts the username (subject) from the Access Token JWT.
     * @param token The Access Token JWT.
     * @return The username (email), or null if extraction fails.
     */
    public String getUserNameFromJwtToken(String token) {
        try {
            // Parses the token using the access key and extracts the subject
            return Jwts.parserBuilder().setSigningKey(getAccessKey()).build()
                    .parseClaimsJws(token).getBody().getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            // Logs error if token is malformed, expired, or invalid
            logger.error("Error extracting username from JWT: {}", e.getMessage());
            return null; // Return null or throw a custom exception
        }
    }

    /**
     * Validates the signature and expiration of an Access Token JWT.
     * @param authToken The Access Token JWT to validate.
     * @return true if the token is valid, false otherwise.
     */
    public boolean validateJwtToken(String authToken) {
        try {
            // Attempts to parse the token using the access key. If successful, it's valid.
            Jwts.parserBuilder().setSigningKey(getAccessKey()).build().parse(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        } catch (SignatureException e) { // Catches specifically signature errors
            logger.error("Invalid JWT signature: {}", e.getMessage());
        }
        // If any exception occurs, the token is invalid
        return false;
    }

    /**
     * Validates a Refresh Token (IF you decide to use JWTs for Refresh Tokens later).
     * @param refreshToken The Refresh Token JWT to validate.
     * @return true if valid.
     */
    // public boolean validateRefreshToken(String refreshToken) {
    //    try {
    //        Jwts.parserBuilder().setSigningKey(getRefreshKey()).build().parse(refreshToken);
    //        return true;
    //    } catch (JwtException | IllegalArgumentException e) {
    //        logger.error("Invalid Refresh Token: {}", e.getMessage());
    //        return false;
    //    }
    // }
}
