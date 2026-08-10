package tn.coconsult.medtrack.notification.listener;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.events.AppointmentCreatedEvent;
import tn.coconsult.medtrack.events.AppointmentStatusChangedEvent;
import tn.coconsult.medtrack.notification.dto.NotificationType;
import tn.coconsult.medtrack.notification.service.AppointmentEmailService;
import tn.coconsult.medtrack.notification.service.AppointmentNotificationService;

/**
 * Consomme les évènements publiés par appointment-service sur l'exchange "appointment.events".
 * Une seule queue, deux routing keys : le dispatch created/status-changed se fait par type de
 * payload (@RabbitHandler), pas par méthode de queue séparée. Chaque évènement déclenche à la
 * fois le push WebSocket au médecin (AppointmentNotificationService) et l'email au patient
 * (AppointmentEmailService) — deux canaux indépendants, un seul point de consommation.
 */
@Component
@RabbitListener(queues = "${medtrack.rabbitmq.appointment-events-queue}")
@RequiredArgsConstructor
public class AppointmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventListener.class);

    private final AppointmentNotificationService appointmentNotificationService;
    private final AppointmentEmailService appointmentEmailService;

    @RabbitHandler
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        log.info("Évènement consommé : rendez-vous {} créé (médecin {}).", event.appointmentId(), event.medecinId());
        appointmentNotificationService.notify(NotificationType.NOUVEAU_RENDEZ_VOUS, event.medecinId(),
                event.appointmentId(), event.data());
        appointmentEmailService.sendConfirmation(event.appointmentId());
    }

    @RabbitHandler
    public void onAppointmentStatusChanged(AppointmentStatusChangedEvent event) {
        log.info("Évènement consommé : rendez-vous {} changement de statut (médecin {}).", event.appointmentId(), event.medecinId());
        appointmentNotificationService.notify(NotificationType.CHANGEMENT_STATUT, event.medecinId(),
                event.appointmentId(), event.data());
        if (event.data().statut() == StatutRendezVous.ANNULE) {
            appointmentEmailService.sendCancellation(event.appointmentId());
        }
    }
}
