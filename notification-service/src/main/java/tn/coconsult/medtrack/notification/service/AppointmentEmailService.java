package tn.coconsult.medtrack.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.Year;
import java.time.format.DateTimeFormatter;

/**
 * Emails patient liés à un rendez-vous (confirmation, annulation, rappel la veille) — distinct
 * d'AppointmentNotificationService qui gère le canal médecin/WebSocket. Même gabarit visuel que
 * les emails de user-service (carte blanche centrée, inline CSS) pour une identité cohérente.
 */
@Service
@RequiredArgsConstructor
public class AppointmentEmailService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEmailService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /** Déclenché par AppointmentEventListener à la création (évènement RabbitMQ, un seul rendez-vous). */
    public void sendConfirmation(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            log.warn("Rendez-vous {} introuvable, email de confirmation non envoyé.", appointmentId);
            return;
        }
        Patient patient = patientRepository.findById(appointment.getPatientId()).orElse(null);
        if (!hasEmail(patient, appointmentId, "de confirmation")) {
            return;
        }
        User medecin = userRepository.findById(appointment.getMedecinId()).orElse(null);

        String body = """
                <p style="margin:0 0 16px 0; font-family:Arial, Helvetica, sans-serif; font-size:15px; color:#5b6573; line-height:1.6;">
                    Bonjour <strong style="color:#1a1f36;">%s %s</strong>, votre rendez-vous est confirmé :
                </p>
                %s
                """.formatted(patient.getPrenom(), patient.getNom(), detailsTable(appointment, medecin, true));

        emailService.sendHtmlEmail(patient.getEmail(), "Medtrack - Confirmation de votre rendez-vous",
                buildCardHtml("Rendez-vous confirmé", body));
    }

    public void sendCancellation(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            log.warn("Rendez-vous {} introuvable, email d'annulation non envoyé.", appointmentId);
            return;
        }
        Patient patient = patientRepository.findById(appointment.getPatientId()).orElse(null);
        if (!hasEmail(patient, appointmentId, "d'annulation")) {
            return;
        }
        User medecin = userRepository.findById(appointment.getMedecinId()).orElse(null);

        String body = """
                <p style="margin:0 0 16px 0; font-family:Arial, Helvetica, sans-serif; font-size:15px; color:#5b6573; line-height:1.6;">
                    Bonjour <strong style="color:#1a1f36;">%s %s</strong>, le rendez-vous suivant a été annulé :
                </p>
                %s
                <p style="margin:16px 0 0 0; font-family:Arial, Helvetica, sans-serif; font-size:15px; color:#5b6573; line-height:1.6;">
                    Contactez le cabinet pour reprogrammer un nouveau rendez-vous si besoin.
                </p>
                """.formatted(patient.getPrenom(), patient.getNom(), detailsTable(appointment, medecin, false));

        emailService.sendHtmlEmail(patient.getEmail(), "Medtrack - Annulation de votre rendez-vous",
                buildCardHtml("Rendez-vous annulé", body));
    }

    public void sendReminder(Appointment appointment, Patient patient, User medecin) {
        if (!hasEmail(patient, appointment.getId(), "de rappel")) {
            return;
        }

        String body = """
                <p style="margin:0 0 16px 0; font-family:Arial, Helvetica, sans-serif; font-size:15px; color:#5b6573; line-height:1.6;">
                    Bonjour <strong style="color:#1a1f36;">%s %s</strong>, pour rappel, vous avez rendez-vous demain :
                </p>
                %s
                """.formatted(patient.getPrenom(), patient.getNom(), detailsTable(appointment, medecin, true));

        emailService.sendHtmlEmail(patient.getEmail(), "Medtrack - Rappel de votre rendez-vous de demain",
                buildCardHtml("Rappel de rendez-vous", body));
    }

    private boolean hasEmail(Patient patient, Long appointmentId, String contexte) {
        if (patient == null) {
            log.warn("Patient introuvable pour le rendez-vous {}, email {} non envoyé.", appointmentId, contexte);
            return false;
        }
        if (!StringUtils.hasText(patient.getEmail())) {
            log.warn("Patient {} sans email, email {} non envoyé pour le rendez-vous {}.",
                    patient.getId(), contexte, appointmentId);
            return false;
        }
        return true;
    }

    private String detailsTable(Appointment appointment, User medecin, boolean avecMotif) {
        String medecinNom = medecin == null ? "-" : "Dr " + medecin.getPrenom() + " " + medecin.getNom();
        String motifLigne = avecMotif ? """
                <tr>
                    <td style="padding:4px 0; font-family:Arial, Helvetica, sans-serif; font-size:14px; color:#8a94a6;">Motif</td>
                    <td style="padding:4px 0; font-family:Arial, Helvetica, sans-serif; font-size:14px; color:#1a1f36; font-weight:bold;">%s</td>
                </tr>
                """.formatted(appointment.getMotif()) : "";

        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="width:100%%; background-color:#f9fafb; border-radius:8px; padding:16px;">
                    <tr>
                        <td style="padding:4px 0; font-family:Arial, Helvetica, sans-serif; font-size:14px; color:#8a94a6;">Date</td>
                        <td style="padding:4px 0; font-family:Arial, Helvetica, sans-serif; font-size:14px; color:#1a1f36; font-weight:bold;">%s à %s</td>
                    </tr>
                    <tr>
                        <td style="padding:4px 0; font-family:Arial, Helvetica, sans-serif; font-size:14px; color:#8a94a6;">Médecin</td>
                        <td style="padding:4px 0; font-family:Arial, Helvetica, sans-serif; font-size:14px; color:#1a1f36; font-weight:bold;">%s</td>
                    </tr>
                    %s
                </table>
                """.formatted(appointment.getDateHeure().format(DATE), appointment.getDateHeure().format(HEURE), medecinNom, motifLigne);
    }

    // Même gabarit (carte blanche centrée, tables role="presentation") que
    // InvitationService/PasswordResetService : contraintes de compatibilité email, pas de <style>.
    private String buildCardHtml(String title, String bodyHtml) {
        int currentYear = Year.now().getValue();
        return """
                <!DOCTYPE html>
                <html lang="fr" xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f5f7;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f4f5f7; width:100%%;">
                    <tr>
                        <td align="center" style="padding:40px 16px;">
                            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px; max-width:600px; background-color:#ffffff; border:1px solid #e3e6ea; border-radius:12px;">
                                <tr>
                                    <td align="center" style="padding:32px 40px 12px 40px; font-family:Arial, Helvetica, sans-serif; font-size:26px; font-weight:bold; color:#1a1f36; letter-spacing:-0.5px;">
                                        Med<span style="color:#2563eb;">Track</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td align="center" style="padding:4px 40px 20px 40px;">
                                        <h1 style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:22px; font-weight:bold; color:#1a1f36; line-height:1.3;">%s</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:0 40px 32px 40px;">
                                        %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:20px 40px; border-top:1px solid #eef0f3;">
                                        <p style="margin:0; font-family:Arial, Helvetica, sans-serif; font-size:12px; color:#8a94a6; line-height:1.5;">© %d MedTrack. Tous droits réservés.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(title, title, bodyHtml, currentYear);
    }
}
