package com.operaciones.backend.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public MailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        this.fromAddress = username == null ? "" : username.trim();
        this.enabled = !this.fromAddress.isBlank();
        if (!this.enabled) {
            log.warn("SMTP deshabilitado: define MAIL_USERNAME (correo Gmail) para enviar correos.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String fromAddress() {
        return fromAddress;
    }

    public boolean sendText(String to, String subject, String body) {
        if (!enabled) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("No se pudo enviar correo a {}: {}", to, ex.getMessage());
            return false;
        }
    }

    public boolean sendHtml(String to, String subject, String htmlBody) {
        if (!enabled) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("No se pudo enviar HTML a {}: {}", to, ex.getMessage());
            return false;
        }
    }
}
