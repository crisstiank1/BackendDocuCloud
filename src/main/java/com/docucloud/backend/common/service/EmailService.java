package com.docucloud.backend.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private final RestClient restClient;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.from-name:DocuCloud}")
    private String fromName;

    @Value("${app.email.sendgrid.api-key}")
    private String sendgridApiKey;

    public EmailService(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://api.sendgrid.com")
                .build();
    }

    @Async
    public void sendWelcome(String to, String name) {
        try {
            sendEmail(
                    to,
                    "¡Bienvenido a DocuCloud!",
                    "<p>Hola " + safe(name) + ",</p>" +
                            "<p>Tu cuenta en <strong>DocuCloud</strong> fue creada exitosamente.</p>" +
                            "<p>Ya puedes subir, organizar y compartir tus archivos.</p>" +
                            "<p>— Equipo DocuCloud</p>"
            );
            log.info("[Email] ✅ Bienvenida enviada a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando welcome a {}: {}", to, e.getMessage(), e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String resetUrl) {
        log.info("[Email] Preparando correo de recuperación para: {}", to);
        try {
            sendEmail(
                    to,
                    "Recuperación de contraseña - DocuCloud",
                    "<p>Recibimos una solicitud para restablecer tu contraseña.</p>" +
                            "<p>Abre este enlace para crear una nueva contraseña (expira en 15 minutos):</p>" +
                            "<p><a href=\"" + resetUrl + "\">Restablecer contraseña</a></p>" +
                            "<p>Si tú no solicitaste esto, ignora este correo.</p>" +
                            "<p>— Equipo DocuCloud</p>"
            );
            log.info("[Email] ✅ Correo de recuperación enviado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando correo de recuperación a {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    @Async
    public void sendShareGranted(String to, String documentName, String permission) {
        try {
            sendEmail(
                    to,
                    "[DocuCloud] Archivo compartido contigo",
                    "<p>Se te compartió el archivo <strong>\"" + safe(documentName) + "\"</strong> " +
                            "con permiso de <strong>" + safe(permission) + "</strong>.</p>" +
                            "<p>Inicia sesión en DocuCloud para verlo.</p>" +
                            "<p>— Equipo DocuCloud</p>"
            );
            log.info("[Email] ✅ Share notificado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando shareGranted a {}: {}", to, e.getMessage(), e);
        }
    }

    @Async
    public void sendShareRevoked(String to, String documentName) {
        try {
            sendEmail(
                    to,
                    "[DocuCloud] Acceso al archivo revocado",
                    "<p>Tu acceso al archivo <strong>\"" + safe(documentName) + "\"</strong> fue revocado.</p>" +
                            "<p>— Equipo DocuCloud</p>"
            );
            log.info("[Email] ✅ Revocación notificada a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando shareRevoked a {}: {}", to, e.getMessage(), e);
        }
    }

    @Async
    public void sendPermissionChanged(String to, String documentName, String newPermission) {
        try {
            sendEmail(
                    to,
                    "[DocuCloud] Permisos del archivo modificados",
                    "<p>El permiso sobre <strong>\"" + safe(documentName) + "\"</strong> fue cambiado a: " +
                            "<strong>" + safe(newPermission) + "</strong>.</p>" +
                            "<p>— Equipo DocuCloud</p>"
            );
            log.info("[Email] ✅ Cambio de permiso notificado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando permissionChanged a {}: {}", to, e.getMessage(), e);
        }
    }

    @Async
    public void sendPasswordChanged(String to) {
        try {
            sendEmail(
                    to,
                    "[DocuCloud] Contraseña actualizada",
                    "<p>Tu contraseña fue cambiada exitosamente.</p>" +
                            "<p>Si no fuiste tú, contacta soporte de inmediato.</p>" +
                            "<p>— Equipo DocuCloud</p>"
            );
            log.info("[Email] ✅ Cambio de contraseña notificado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando passwordChanged a {}: {}", to, e.getMessage(), e);
        }
    }

    private void sendEmail(String to, String subject, String html) {
        Map<String, Object> body = Map.of(
                "personalizations", List.of(
                        Map.of(
                                "to", List.of(Map.of("email", to)),
                                "subject", subject
                        )
                ),
                "from", Map.of(
                        "email", fromEmail,
                        "name", fromName
                ),
                "content", List.of(
                        Map.of(
                                "type", "text/html",
                                "value", html
                        )
                )
        );

        restClient.post()
                .uri("/v3/mail/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + sendgridApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
