package com.docucloud.backend.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ─── Bienvenida al registrarse ────────────────────────────────────────────
    @Async
    public void sendWelcome(String to, String name) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(fromEmail);
            msg.setSubject("¡Bienvenido a DocuCloud!");
            msg.setText(
                    "Hola " + (name != null ? name : "") + ",\n\n" +
                            "Tu cuenta en DocuCloud fue creada exitosamente.\n" +
                            "Ya puedes subir, organizar y compartir tus Archivos.\n\n" +
                            "— Equipo DocuCloud"
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Bienvenida enviada a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando welcome a {}: {}", to, e.getMessage());
            // Silencioso: no bloquea el registro
        }
    }

    // ─── Recuperación de contraseña ───────────────────────────────────────────
    @Async
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
                            "Si tú no solicitaste esto, ignora este correo.\n\n" +
                            "— Equipo DocuCloud"
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Correo enviado exitosamente a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando correo a {}: {}", to, e.getMessage(), e);
            throw e; // relanza — el reset SÍ debe fallar si no llega el email
        }
    }

    // ─── Share concedido ──────────────────────────────────────────────────────
    @Async
    public void sendShareGranted(String to, String documentName, String permission) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(fromEmail);
            msg.setSubject("[DocuCloud] Archivo compartido contigo");
            msg.setText(
                    "Se te compartió el Archivo \"" + documentName + "\" " +
                            "con permiso de " + permission + ".\n" +
                            "Inicia sesión en DocuCloud para verlo.\n\n" +
                            "— Equipo DocuCloud"
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Share notificado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando shareGranted a {}: {}", to, e.getMessage());
            // Silencioso: no bloquea el share
        }
    }

    // ─── Share revocado ───────────────────────────────────────────────────────
    @Async
    public void sendShareRevoked(String to, String documentName) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(fromEmail);
            msg.setSubject("[DocuCloud] Acceso al Archivo revocado");
            msg.setText(
                    "Tu acceso al Archivo \"" + documentName + "\" fue revocado.\n\n" +
                            "— Equipo DocuCloud"
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Revocación notificada a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando shareRevoked a {}: {}", to, e.getMessage());
        }
    }

    // ─── Permiso modificado ───────────────────────────────────────────────────
    @Async
    public void sendPermissionChanged(String to, String documentName, String newPermission) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(fromEmail);
            msg.setSubject("[DocuCloud] Permisos del Archivo modificados");
            msg.setText(
                    "El permiso sobre \"" + documentName + "\" fue cambiado a: " +
                            newPermission + ".\n\n" +
                            "— Equipo DocuCloud"
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Cambio de permiso notificado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando permissionChanged a {}: {}", to, e.getMessage());
        }
    }

    // ─── Contraseña cambiada ──────────────────────────────────────────────────
    @Async
    public void sendPasswordChanged(String to) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setFrom(fromEmail);
            msg.setSubject("[DocuCloud] Contraseña actualizada");
            msg.setText(
                    "Tu contraseña fue cambiada exitosamente.\n" +
                            "Si no fuiste tú, contacta soporte de inmediato.\n\n" +
                            "— Equipo DocuCloud"
            );
            mailSender.send(msg);
            log.info("[Email] ✅ Cambio de contraseña notificado a: {}", to);
        } catch (Exception e) {
            log.error("[Email] ❌ Error enviando passwordChanged a {}: {}", to, e.getMessage());
        }
    }
}
