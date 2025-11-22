package com.docucloud.backend.dtos.response;

import java.util.Set;

public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private Set<String> roles;

    public JwtResponse(String accessToken, String refreshToken, Long userId, String email, Set<String> roles) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.roles = roles;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public Set<String> getRoles() { return roles; }
}
