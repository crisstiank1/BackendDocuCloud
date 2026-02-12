package com.docucloud.backend.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@Service
public class RecaptchaService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.recaptcha.secret}")
    private String secret;

    @Value("${google.recaptcha.verify-url}")
    private String verifyUrl;

    public boolean verify(String recaptchaToken, String remoteIp) {

        if (recaptchaToken == null || recaptchaToken.isBlank()) {
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", recaptchaToken);

        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(form, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(verifyUrl, request, Map.class);

        if (response.getBody() == null) return false;

        return Boolean.TRUE.equals(response.getBody().get("success"));
    }
}
