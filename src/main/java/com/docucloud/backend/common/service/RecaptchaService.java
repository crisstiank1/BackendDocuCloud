package com.docucloud.backend.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class RecaptchaService {

    private final RestTemplate restTemplate;

    @Value("${google.recaptcha.secret}")
    private String secret;

    @Value("${google.recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
    private String verifyUrl;

    public RecaptchaService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean verify(String recaptchaToken) {
        if (recaptchaToken == null || recaptchaToken.isBlank()) return false;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", recaptchaToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(verifyUrl, request, Map.class);

            Map body = response.getBody();
            if (body == null) return false;

            boolean success = Boolean.TRUE.equals(body.get("success"));
            if (!success) {
                Object codes = body.get("error-codes"); // puede venir cuando falla
                if (codes instanceof List<?>) {
                    // opcional: log.debug("reCAPTCHA failed: {}", codes);
                }
            }
            return success;

        } catch (RestClientException ex) {
            return false;
        }
    }
}
