package com.aec.prodsrv.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value; // Para inyectar el correo del admin desde properties
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

        private static final Logger log = LoggerFactory.getLogger(EmailService.class);


    @Autowired
    private JavaMailSender mailSender;

    // Inyecta el correo del administrador desde application.yml/properties
    @Value("${admin.email.recipient:support@aecblock.com}")
    private String adminEmailRecipient;

    // Inyecta el correo del remitente desde application.yml/properties
    @Value("${admin.email.sender:support@aecblock.com}")
    private String senderEmail;


   private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No hay destinatario para el email: {}", subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "utf-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(msg);
            log.info("Email enviado a {}: {}", toEmail, subject);
        } catch (MessagingException e) {
            log.error("Error enviando email a {}: {}", toEmail, e.getMessage(), e);
        }
    }


    public void sendNewProductForReviewEmail(String uploaderUsername,
                                             Long productId,
                                             String productName,
                                             List<String> categorias,
                                             List<String> especialidades,
                                             List<String> portadaUrls) {
        String subject = "📑 Nuevo producto enviado para revisión";
        String cats = (categorias == null || categorias.isEmpty()) ? "N/A" : String.join(", ", categorias);
        String specs = (especialidades == null || especialidades.isEmpty()) ? "N/A" : String.join(", ", especialidades);

        StringBuilder fotosHtml = new StringBuilder();
        if (portadaUrls != null && !portadaUrls.isEmpty()) {
            for (String u : portadaUrls) {
                fotosHtml.append("<div style='margin:6px 0;'>")
                         .append("<a href='").append(u).append("' target='_blank'>")
                         .append(u)
                         .append("</a>")
                         .append("</div>");
            }
        } else {
            fotosHtml.append("<em>Sin imágenes de portada</em>");
        }

        String html = """
            <html>
              <body style="font-family: Arial, sans-serif; line-height:1.6;">
                <h2>Nuevo producto pendiente de revisión</h2>
                <p><strong>Creador:</strong> %s</p>
                <p><strong>ID del producto:</strong> %d</p>
                <p><strong>Nombre del producto:</strong> %s</p>
                <p><strong>Categorías:</strong> %s</p>
                <p><strong>Especialidades:</strong> %s</p>
                <p><strong>Imágenes de portada:</strong><br/>%s</p>
                <p>Ingresa al panel de administración para revisarlo y decidir su publicación.</p>
              </body>
            </html>
        """.formatted(uploaderUsername, productId, productName, cats, specs, fotosHtml.toString());

        sendHtmlEmail(adminEmailRecipient, subject, html); // reutiliza tu método existente
    }

       public void sendProductApprovedEmail(String toEmail,
                                         String uploaderUsername,
                                         Long productId,
                                         String productName,
                                         String portadaUrlOpcional,
                                         String comentarioAdminOpcional) {
        String subject = "✅ Tu producto ha sido APROBADO";
        String portadaHtml = (portadaUrlOpcional == null || portadaUrlOpcional.isBlank())
                ? "<em>Sin portada</em>"
                : "<a href='" + portadaUrlOpcional + "' target='_blank'>Ver portada</a>";

        String comentarioHtml = (comentarioAdminOpcional == null || comentarioAdminOpcional.isBlank())
                ? ""
                : ("<p><strong>Comentario del administrador:</strong><br/>" + comentarioAdminOpcional + "</p>");

        String html = """
            <html>
              <body style="font-family: Arial, sans-serif; line-height:1.6;">
                <h2>¡Felicidades %s!</h2>
                <p>Tu producto <strong>%s</strong> (ID: %d) fue <strong>aprobado</strong> y ya puede publicarse en el marketplace.</p>
                <p>%s</p>
                <p>%s</p>
                <p>Gracias por crear en AECBlock.</p>
              </body>
            </html>
        """.formatted(uploaderUsername, productName, productId, portadaHtml, comentarioHtml);

        sendHtmlEmail(toEmail, subject, html);
    }

    // === NUEVO: rechazado (opcional) ===
    public void sendProductRejectedEmail(String toEmail,
                                         String uploaderUsername,
                                         Long productId,
                                         String productName,
                                         String comentarioAdminOpcional) {
        String subject = "❌ Tu producto fue RECHAZADO";
        String comentarioHtml = (comentarioAdminOpcional == null || comentarioAdminOpcional.isBlank())
                ? "<em>Sin comentarios adicionales.</em>"
                : ("<strong>Motivo:</strong><br/>" + comentarioAdminOpcional);

        String html = """
            <html>
              <body style="font-family: Arial, sans-serif; line-height:1.6;">
                <h2>Hola %s</h2>
                <p>Tu producto <strong>%s</strong> (ID: %d) fue <strong>rechazado</strong>.</p>
                <p>%s</p>
                <p>Puedes revisarlo, ajustar y reenviarlo para una nueva evaluación.</p>
              </body>
            </html>
        """.formatted(uploaderUsername, productName, productId, comentarioHtml);

        sendHtmlEmail(toEmail, subject, html);
    }

}
