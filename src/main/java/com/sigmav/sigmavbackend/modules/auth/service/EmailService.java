package com.sigmav.sigmavbackend.modules.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /**
     * Envía un email HTML genérico.
     *
     * @param toEmail  destinatario
     * @param subject  asunto
     * @param htmlBody cuerpo del mensaje (HTML)
     */
    public void sendHtmlEmail(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("No se pudo enviar el email", e);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String token, String academyName) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String subject = "Recupera tu contraseña — " + academyName;
        sendHtmlEmail(toEmail, subject, buildResetHtml(academyName, resetLink));
    }

    private String buildResetHtml(String academyName, String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"/></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08)">
                <tr>
                  <td style="background:#6366f1;padding:32px 40px;text-align:center">
                    <h1 style="color:#fff;margin:0;font-size:22px;font-weight:700">%s</h1>
                  </td>
                </tr>
                <tr>
                  <td style="padding:40px">
                    <h2 style="color:#111;font-size:18px;margin:0 0 16px">Recupera tu contraseña</h2>
                    <p style="color:#555;line-height:1.6;margin:0 0 24px">
                      Recibiste este email porque solicitaste restablecer tu contraseña en
                      <strong>%s</strong>. Haz clic en el botón para crear una nueva contraseña.
                      El enlace vence en <strong>1 hora</strong>.
                    </p>
                    <a href="%s"
                       style="display:inline-block;background:#6366f1;color:#fff;padding:14px 28px;border-radius:8px;text-decoration:none;font-weight:600;font-size:15px">
                      Restablecer contraseña
                    </a>
                    <p style="color:#999;font-size:12px;margin:32px 0 0">
                      Si no solicitaste este cambio, ignora este email. Tu contraseña no cambiará.
                    </p>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(academyName, academyName, resetLink);
    }
}
