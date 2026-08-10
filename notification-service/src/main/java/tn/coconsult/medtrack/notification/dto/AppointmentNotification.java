package tn.coconsult.medtrack.notification.dto;

import java.time.LocalDateTime;

/**
 * Payload envoyé sur /user/queue/appointments. Forme alignée sur
 * AppointmentNotificationPayload côté frontend (id/type/appointmentId/createdAt/data) : id sert
 * de clé de déduplication après reconnexion STOMP, createdAt à l'affichage relatif ("il y a 5
 * min"), voir notification.model.ts. Ne pas ajouter de champ "message" pré-formaté : le frontend
 * traduit lui-même à partir de data (i18n).
 */
public record AppointmentNotification(
        String id,
        NotificationType type,
        Long appointmentId,
        LocalDateTime createdAt,
        AppointmentNotificationData data
) {
}
