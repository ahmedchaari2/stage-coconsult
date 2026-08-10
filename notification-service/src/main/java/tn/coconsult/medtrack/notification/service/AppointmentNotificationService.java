package tn.coconsult.medtrack.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.notification.dto.AppointmentNotification;
import tn.coconsult.medtrack.notification.dto.AppointmentNotificationData;
import tn.coconsult.medtrack.notification.dto.NotificationType;
import tn.coconsult.medtrack.notification.model.Notification;
import tn.coconsult.medtrack.notification.repository.NotificationRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.user.model.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Émission des notifications de rendez-vous : persistance (une ligne {@link Notification} par
 * destinataire, jamais partagée) en plus du push WebSocket temps réel sur un canal privé par
 * utilisateur (/queue/appointments, jamais de broadcast). Les deux se font dans {@link #send}.
 * Seul le médecin assigné au rendez-vous est notifié (création, changement de statut, rappel) —
 * pas de diffusion aux ADMIN : le push cible l'acteur concerné par l'action, pas tout le monde
 * ayant accès en lecture aux données (le scope d'accès de l'ADMIN reste inchangé par ailleurs).
 */
@Service
@RequiredArgsConstructor
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);
    private static final String USER_DESTINATION = "/queue/appointments";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;
    private final NotificationRepository notificationRepository;

    public void notify(NotificationType type, Long medecinId, Long appointmentId, AppointmentNotificationData data) {
        AppointmentNotification payload = new AppointmentNotification(
                UUID.randomUUID().toString(), type, appointmentId, LocalDateTime.now(), data);

        send(medecinId, payload);
    }

    /**
     * Persiste puis pousse en temps réel, pour un destinataire donné : la persistance garde une
     * trace consultable via GET /api/notifications même si le destinataire n'a pas de session
     * WebSocket active au moment de l'envoi (comportement normal du broker en mémoire, pas de
     * file d'attente pour utilisateur déconnecté). On vérifie via SimpUserRegistry pour distinguer
     * ce cas en log plutôt que de le laisser passer en silent failure.
     */
    private void send(Long userId, AppointmentNotification payload) {
        Notification notification = new Notification();
        notification.setDestinataireId(userId);
        notification.setType(payload.type());
        notification.setAppointmentId(payload.appointmentId());
        notification.setDateHeure(payload.data().dateHeure());
        notificationRepository.save(notification);

        String name = String.valueOf(userId);
        boolean connected = simpUserRegistry.getUser(name) != null;

        messagingTemplate.convertAndSendToUser(name, USER_DESTINATION, payload);

        if (connected) {
            log.debug("Notification {} (rendez-vous {}) persistée et envoyée à l'utilisateur {}.",
                    payload.type(), payload.appointmentId(), userId);
        } else {
            log.info("Notification {} (rendez-vous {}) persistée, NON délivrée en temps réel : utilisateur {} sans session WebSocket active.",
                    payload.type(), payload.appointmentId(), userId);
        }
    }

    public static AppointmentNotificationData toData(Patient patient, User medecin, LocalDateTime dateHeure) {
        return toData(patient, medecin, dateHeure, null);
    }

    public static AppointmentNotificationData toData(Patient patient, User medecin, LocalDateTime dateHeure, StatutRendezVous statut) {
        return new AppointmentNotificationData(
                patient == null ? null : patient.getNom(),
                patient == null ? null : patient.getPrenom(),
                medecin == null ? null : medecin.getNom(),
                medecin == null ? null : medecin.getPrenom(),
                dateHeure,
                statut);
    }
}
