package com.docucloud.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

    @NotBlank
    @Email
    private String email;

    private String recaptchaToken;

    public ForgotPasswordRequest() {}

    public ForgotPasswordRequest(String email, String recaptchaToken) {
        this.email = email;
        this.recaptchaToken = recaptchaToken;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRecaptchaToken() { return recaptchaToken; }
    public void setRecaptchaToken(String recaptchaToken) { this.recaptchaToken = recaptchaToken; }
}
