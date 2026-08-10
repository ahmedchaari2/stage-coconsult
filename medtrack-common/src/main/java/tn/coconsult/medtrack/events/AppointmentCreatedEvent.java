package tn.coconsult.medtrack.events;

import tn.coconsult.medtrack.notification.dto.AppointmentNotificationData;

public record AppointmentCreatedEvent(
        Long appointmentId,
        Long medecinId,
        AppointmentNotificationData data
) {
}
