package com.operaciones.backend.mail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Envía muestras HTML de los correos FinanTech (bienvenida, verificación, recordatorio).
 * Solo permite destino = MAIL_USERNAME para no abrir un relay abierto.
 */
@RestController
@RequestMapping("/api/mail")
public class MailPreviewController {

    private final MailService mailService;
    private final AppUrlProperties appUrls;

    public MailPreviewController(MailService mailService, AppUrlProperties appUrls) {
        this.mailService = mailService;
        this.appUrls = appUrls;
    }

    @PostMapping("/preview-samples")
    public Map<String, Object> sendSamples(@RequestBody(required = false) Map<String, String> body) {
        if (!mailService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMTP no configurado");
        }
        String to = body == null ? null : body.get("to");
        if (to == null || to.isBlank()) {
            to = mailService.fromAddress();
        }
        to = to.trim();
        if (!to.equalsIgnoreCase(mailService.fromAddress())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo se pueden enviar muestras a " + mailService.fromAddress());
        }

        String verifyUrl = appUrls.absoluteFrontend("/verificar-correo/demo-token-muestra");
        String profileUrl = appUrls.absoluteFrontend("/perfil");
        String payUrl = appUrls.absoluteFrontend("/pagos/cuotas-cliente");

        boolean welcome = mailService.sendHtml(
                to,
                "[Muestra] FinanTech S.A. — Bienvenido, registra y verifica tu correo",
                MailTemplates.welcomeClient("Alejandro", "61030865", profileUrl));
        boolean verify = mailService.sendHtml(
                to,
                "[Muestra] FinanTech S.A. — Verifica tu correo",
                MailTemplates.verifyEmail("Alejandro", verifyUrl));
        boolean reminder = mailService.sendHtml(
                to,
                "[Muestra] FinanTech S.A. · Recordatorio de cuota",
                MailTemplates.paymentReminder(
                        "Alejandro",
                        "PT-1006",
                        "#1 · vence 05/11/2026",
                        "S/ 30.61",
                        payUrl));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("to", to);
        out.put("welcome", welcome);
        out.put("verify", verify);
        out.put("reminder", reminder);
        out.put("status", welcome && verify && reminder ? "ok" : "partial");
        return out;
    }
}
