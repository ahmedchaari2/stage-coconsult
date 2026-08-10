package tn.coconsult.medtrack.events;

import tn.coconsult.medtrack.notification.dto.AppointmentNotificationData;

public record AppointmentStatusChangedEvent(
        Long appointmentId,
        Long medecinId,
        AppointmentNotificationData data
) {
}
