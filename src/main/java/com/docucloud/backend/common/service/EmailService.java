package com.docucloud.backend.common.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }


    public void sendPasswordResetEmail(String to, String resetUrl) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setFrom(System.getenv("SMTP_USERNAME"));
        msg.setSubject("Recuperación de contraseña");
        msg.setText(
                "Recibimos una solicitud para restablecer tu contraseña.\n\n" +
                        "Abre este enlace para crear una nueva contraseña (expira pronto):\n" +
                        resetUrl + "\n\n" +
                        "Si tú no solicitaste esto, ignora este correo."
        );
        mailSender.send(msg);
    }
}

