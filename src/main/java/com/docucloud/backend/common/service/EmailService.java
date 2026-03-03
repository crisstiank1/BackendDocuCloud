package com.docucloud.backend.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // ✅ Lee desde application.properties, no de variable de entorno
    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String to, String resetUrl) {
        log.info("[Email] Preparando correo para: {} desde: {}", to, fromEmail);
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(fromEmail);
            msg.setSubject("Recuperación de contraseña - DocuCloud");
            msg.setText(
                    "Recibimos una solicitud para restablecer tu contraseña.\n\n" +
                            "Abre este enlace para crear una nueva contraseña (expira en 15 minutos):\n" +
                            resetUrl + "\n\n" +
                            "Si tú no solicitaste esto, ignora este correo."
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Correo enviado exitosamente a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando correo a {}: {}", to, e.getMessage(), e);
            throw e; // relanza para que no quede oculto
        }
    }

}
