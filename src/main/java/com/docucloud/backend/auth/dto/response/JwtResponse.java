package com.docucloud.backend.auth.dto.response;

import java.util.Set;

public class JwtResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private Set<String> roles;
    private String name;

    public JwtResponse(String accessToken, String refreshToken,
                       Long userId, String email, Set<String> roles, String name) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        this.userId       = userId;
        this.email        = email;
        this.roles        = roles;
        this.name         = name;
    }

    public String      getAccessToken()  { return accessToken; }
    public String      getRefreshToken() { return refreshToken; }
    public String      getTokenType()    { return tokenType; }
    public Long        getUserId()       { return userId; }
    public String      getEmail()        { return email; }
    public Set<String> getRoles()        { return roles; }
    public String      getName()         { return name; }
}
