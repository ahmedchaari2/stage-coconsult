package tn.coconsult.medtrack.appointment.client;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.coconsult.medtrack.events.AppointmentCreatedEvent;
import tn.coconsult.medtrack.events.AppointmentStatusChangedEvent;

/**
 * Publie sur RabbitMQ plutôt que d'appeler notification-service en HTTP (voir l'ancien
 * NotificationServiceClient/AppointmentNotificationClient, supprimés). Fire-and-forget côté
 * appelant : si RabbitMQ ou notification-service sont temporairement indisponibles, le message
 * reste en file (queue durable) et sera traité au retour du consommateur — pas de fallback 503
 * à gérer ici, contrairement à un appel Feign synchrone.
 */
@Component
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${medtrack.rabbitmq.appointment-events-exchange}")
    private String exchangeName;

    @Value("${medtrack.rabbitmq.appointment-created-routing-key}")
    private String createdRoutingKey;

    @Value("${medtrack.rabbitmq.appointment-status-changed-routing-key}")
    private String statusChangedRoutingKey;

    public void publishCreated(AppointmentCreatedEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, createdRoutingKey, event);
        log.info("Évènement publié : rendez-vous {} créé (routing key {}).", event.appointmentId(), createdRoutingKey);
    }

    public void publishStatusChanged(AppointmentStatusChangedEvent event) {
        rabbitTemplate.convertAndSend(exchangeName, statusChangedRoutingKey, event);
        log.info("Évènement publié : rendez-vous {} changement de statut (routing key {}).", event.appointmentId(), statusChangedRoutingKey);
    }
}
