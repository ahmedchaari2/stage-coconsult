package tn.coconsult.medtrack.user.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${medtrack.mail.from:noreply@medtrack.tn}")
    private String from;

    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    /**
     * Envoi asynchrone (pool emailExecutor, voir AsyncConfig) : l'appel SMTP vers Gmail prend
     * plusieurs secondes, l'appelant n'a pas à l'attendre — au moment où l'email part, il a déjà
     * répondu et persisté ce qu'il fallait (invitation, token de reset). L'échec est donc journalisé
     * ici, jamais propagé : personne ne l'attend plus, et la donnée métier reste valide en base
     * (l'invitation existe, l'ADMIN peut la voir et la relancer). On catche large : au-delà de
     * MessagingException (construction du message), c'est surtout MailException (SMTP down/timeout,
     * runtime) qui remonte de send().
     */
    @Async("emailExecutor")
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email envoyé à {} (sujet : {}).", to, subject);
        } catch (jakarta.mail.MessagingException | org.springframework.mail.MailException e) {
            log.error("Échec de l'envoi de l'email à {} (sujet : {}). La donnée associée reste en base.",
                    to, subject, e);
        }
    }
}
